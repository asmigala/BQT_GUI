package qe.jenkins;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qe.exception.JenkinsException;
import qe.jenkins.JenkinsActiveConfiguration.JenkinsStatus;

/**
 * Utilities for jenkins' XML API.
 * 
 * @author jdurani
 *
 */
public class JenkinsXMLAPIPUtils {
   
    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JenkinsXMLAPIPUtils.class);
    
    /**
     * Private constructor - utils class.
     */
    private JenkinsXMLAPIPUtils() {}
    
    /**
     * Returns all view names from document {@code doc}.
     * 
     * @param doc
     * @return
     */
    public static List<String> getViews(Document doc){
        LOGGER.debug("Getting jenkins view names from document {}.", doc.toString());
        return getTextOfElements(doc, "view > name");
    }
    
    /**
     * Returns all job names from document {@code doc}.
     * 
     * @param doc
     * @return
     */
    public static List<String> getJobs(Document doc){
        LOGGER.debug("Getting jenkins job names from document {}.", doc.toString());
        return getTextOfElements(doc, "job > name");
    }
    
    /**
     * Builds and returns and instance of {@link JenkinsJob} from document {@code doc}.
     * 
     * @param doc
     * @return
     * @throws JenkinsException
     */
    public static JenkinsJob getJob(Document doc) throws JenkinsException{
        LOGGER.debug("Getting jenkins job from document {}.", doc.toString());
        //we support only matrix projects
        List<String> jobUrls = getTextOfElements(doc, "matrixProject > url");
        System.out.println("Job urls: " + jobUrls.size());
        if(jobUrls.isEmpty()){
            throw new JenkinsException("Document contains no url for jenkins job.");
        }
        List<String> jobNames = getTextOfElements(doc, "matrixProject > name");
        System.out.println("Job names: " + jobNames.size());
        if(jobNames.isEmpty()){
            throw new JenkinsException("Document contains no name for jenkins job.");
        }
        JenkinsJob job = new JenkinsJob();
        String jobUrl = jobUrls.get(0);
        job.setUrl(jobUrl);
        job.setName(jobNames.get(0));
        Elements buildElements = doc.select("matrixProject > build");
        for(Element buildElem : buildElements){
            String number = buildElem.select("number").get(0).text();
            String buildUrl = buildElem.select("url").get(0).text();
            Document buildDoc = JenkinsManager.getDocument(JenkinsManager.addApi(new StringBuilder(buildUrl)).toString());
            List<String> runs = getTextOfElements(buildDoc, "matrixBuild > run > url");
            JenkinsBuild build = new JenkinsBuild();
            build.setBuildNumber(number);
            build.setUrl(buildUrl);
            boolean first = true;
            for(String run : runs){
                String nodeId = run.substring(jobUrl.length(), run.length() - number.length() - 2);
                int idx1 = nodeId.indexOf('=');
                int idx2 = nodeId.lastIndexOf('=');
                if(idx1 == -1 || idx2 == idx1){
                    throw new JenkinsException("Job label has unexpected name (less than 2 occurrences of \"=\"): " + nodeId);
                }
                int idx3 = nodeId.indexOf(",");
                if(idx3 == -1){
                    throw new JenkinsException("Job label has unexpected name (no \",\"): " + nodeId);
                }
                String xLabel = nodeId.substring(0, idx1);
                String yLabel = nodeId.substring(idx3 + 1, idx2);
                String xValue = nodeId.substring(idx1 + 1, idx3);
                String yValue = nodeId.substring(idx2 + 1, nodeId.length());
                if(first){
                    first = false;
                    build.setxLabel(xLabel);
                    build.setyLabel(yLabel);
                }
                JenkinsActiveConfiguration activeConfiguration = new JenkinsActiveConfiguration();
                activeConfiguration.setUrl(run);
                activeConfiguration.setxValue(xValue);
                activeConfiguration.setyValue(yValue);
                try{
                    build.addActiveConfiguration(activeConfiguration);
                } catch (JenkinsException ex){
                    LOGGER.warn("Exception while adding active configuration: " + ex.getMessage());
                }
            }
            job.addBuild(build);
        }
        return job;
    }
    
    public static boolean getBuildingStatus(Document doc){
        Element root = doc.child(0);
        String bool; 
        if(root.tagName().equals("building")){
            bool = getTextOfElements(doc, "building").get(0);
        } else {
            bool = getTextOfElements(doc, root.tagName() + " > building").get(0);
        }
        return Boolean.valueOf(bool);
    }
    
    public static JenkinsStatus getStatus(Document doc){
        Element root = doc.child(0);
        String status;
        if(root.tagName().equals("result")){
            status = getTextOfElements(doc, "result").get(0);
        } else {
            status = getTextOfElements(doc, root.tagName() + " > result").get(0);
        }
        return JenkinsStatus.valueOf(status);
    }
    
    /**
     * Returns list of texts of tags in document {@code doc} that fits {@code cssQuery}.
     * 
     * @param doc
     * @param cssQuery
     * @return
     */
    private static List<String> getTextOfElements(Document doc, String cssQuery){
        List<String> texts = new ArrayList<>();
        Elements names = doc.select(cssQuery);
        for(int i = 0; i < names.size(); i++){
            Element name = names.get(i);
            texts.add(name.text());
        }
        return texts;
    }
}

















