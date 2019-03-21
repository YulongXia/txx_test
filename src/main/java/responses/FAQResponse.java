package responses;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.Instruction;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.faq.FaqAnswer;
import ai.hual.labrador.faq.FaqRankResult;
import ai.hual.labrador.nlg.ResponseAct;
import ai.hual.labrador.nlu.constants.SystemIntents;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import responses.KnowledgeQueryResponse;

public class FAQResponse {

    private static Logger logger = LoggerFactory.getLogger(FAQResponse.class);

    private AccessorRepository accessorRepository;

    private final static List<String> DEFAULT_RECOMMEND = Arrays.asList("孕妇医疗险的核保尺度","高血压医疗险的核保尺度","重疾险");

    private final static int MAX_RECOMMENDS = 10;

    private final static String BOT_NAME = "taixingxiao_test";

    public FAQResponse(AccessorRepository accessorRepository) {
        this.accessorRepository = accessorRepository;
    }

    public ResponseExecutionResult faq(Context context) {
        return faq(context, true);
    }

    public ResponseExecutionResult faq(Context context, boolean useChatting) {
        logger.debug("FAQ response. useChatting: {}", useChatting);
        List<String> recommends = getRecommendations();
        recommends = KnowledgeQueryResponse.processRecommendations(recommends,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setInstructions(new ArrayList<>());

        FaqAnswer answer = (FaqAnswer) context.getSlots().get("sys.faqAnswer");

        if (answer != null && answer.getHits() != null) {
            for (FaqRankResult hit : answer.getHits()) {
                if (hit == null) {
                    continue;
                }
                boolean chatting = "chatting".equals(hit.getCategory());
                if (useChatting || !chatting) {
                    if (chatting) {
//                        result.getInstructions().add(new Instruction("msginfo_chat")
//                                .addParam("question", hit.getQuestion())
//                                .addParam("quesId", hit.getQaid())
//                                .addParam("answer", hit.getAnswer())
//                                .addParam("ansId", hit.getQaid())
//                                .addParam("score", hit.getScore()));

                        result.getInstructions().add(new Instruction("recommendation")
                                .addParam("title", "下列问题可能对您有帮助")
                                .addParam("items", recommends));
                        result.getInstructions().add(new Instruction("input_button")
                                .addParam("buttons", Arrays.asList("热门问题")));
                        result.setResponseAct(new ResponseAct("answer")
                                .put("result", hit.getAnswer()));
                    } else {
                        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByFAQ(hit.getQaid());
                        relations = relations.isEmpty() ? null : relations;
                        result.getInstructions().add(new Instruction("msginfo_faq_a")
                                .addParam("title", hit.getQuestion())
                                .addParam("quesId", hit.getQaid())
                                .addParam("answer", hit.getAnswer())
                                .addParam("ansId", hit.getQaid())
                                .addParam("score", hit.getScore())
                                .addParam("relations", relations));
                        result.setResponseAct(new ResponseAct("faq")
                                .put("result", hit.getAnswer())
                                .put("question", hit.getQuestion())
                                .put("relations", relations));
                    }
                    return result;
                }
            }
        }

        result.setResponseAct(new ResponseAct(SystemIntents.UNKNOWN));
        result.getInstructions().add(new Instruction("msginfo_more")
                .addParam("answer", accessorRepository.getNLG().generate(result.getResponseAct())));
        return result;
    }

    public static List<String> getRecommendations(){
        Properties prop = getProperties();
        String recommend_url = (String)prop.get("recommend_url");
        if(recommend_url == null){
            return DEFAULT_RECOMMEND;
        }
        HttpPost post = null;
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            post = new HttpPost(recommend_url);
            // 构造消息头
            post.setHeader("Content-type", "application/json; charset=utf-8");

            // 构建消息实体
            JsonObject jsonobj = new JsonObject();
            jsonobj.put("botName" ,BOT_NAME);
            StringEntity entity = new StringEntity(jsonobj.toString(), Charset.forName("UTF-8"));
            entity.setContentEncoding("UTF-8");
            // 发送Json格式的数据请求
            entity.setContentType("application/json");
            post.setEntity(entity);

            HttpResponse response = httpClient.execute(post);

            // 检验返回码
            int statusCode = response.getStatusLine().getStatusCode();
            if(statusCode == HttpStatus.SC_OK){
                InputStream content = response.getEntity().getContent();
                JsonObject json_content = JSON.parse(content);
                JsonArray ja = json_content.get("msg").getAsObject().get("recommend").getAsArray();
                List<String> recommends = ja.stream().map(x -> x.getAsString().value()).collect(Collectors.toList());
                if(recommends.size() > MAX_RECOMMENDS)
                {
                    int i = 0;
                    List<String> randoms = new ArrayList<>();
                    while(i < MAX_RECOMMENDS){
                        Random rand =new Random();
                        int j = rand.nextInt(recommends.size());
                        randoms.add(recommends.remove(j));
                        i++;
                    }
                    return randoms;
                }
                return recommends;
            }
        } catch(Exception e) {
            return DEFAULT_RECOMMEND;
        }
        return DEFAULT_RECOMMEND;
    }

    public static Properties getProperties() {
        final String CONFIG_FILE_PATH = "/remoteapiconfig.properties";
        Properties prop = new Properties();
        try (Reader reader = new InputStreamReader(FAQResponse.class.getResourceAsStream(CONFIG_FILE_PATH),StandardCharsets.UTF_8)) {
            prop.load(reader);
        } catch (IOException ex) {
            System.out.println(ex.toString());
            System.out.println("Could not find file " + CONFIG_FILE_PATH);
        }
        return prop;
    }



    public static List<String> getRecommendations(String query){
        Properties prop = getProperties();
        String recommend_url = (String)prop.get("recommend_url");
        if(recommend_url == null){
            return DEFAULT_RECOMMEND;
        }
        HttpPost post = null;
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            post = new HttpPost(recommend_url);
            // 构造消息头
            post.setHeader("Content-type", "application/json; charset=utf-8");

            // 构建消息实体
            JsonObject jsonobj = new JsonObject();
            jsonobj.put("botName" ,BOT_NAME);
            jsonobj.put("input",query);
            StringEntity entity = new StringEntity(jsonobj.toString(), Charset.forName("UTF-8"));
            entity.setContentEncoding("UTF-8");
            // 发送Json格式的数据请求
            entity.setContentType("application/json");
            post.setEntity(entity);

            HttpResponse response = httpClient.execute(post);

            // 检验返回码
            int statusCode = response.getStatusLine().getStatusCode();
            if(statusCode == HttpStatus.SC_OK){
                InputStream content = response.getEntity().getContent();
                JsonObject json_content = JSON.parse(content);
                JsonArray ja = json_content.get("msg").getAsObject().get("recommend").getAsArray();
                List<String> recommends = ja.stream().map(x -> x.getAsString().value()).collect(Collectors.toList());
                if(recommends.size() > MAX_RECOMMENDS)
                {
                    int i = 0;
                    List<String> randoms = new ArrayList<>();
                    while(i < MAX_RECOMMENDS){
                        Random rand =new Random();
                        int j = rand.nextInt(recommends.size());
                        randoms.add(recommends.remove(j));
                        i++;
                    }
                    return randoms;
                }
                return recommends;
            }
        } catch(Exception e) {
            return DEFAULT_RECOMMEND;
        }
        return DEFAULT_RECOMMEND;
    }

}
