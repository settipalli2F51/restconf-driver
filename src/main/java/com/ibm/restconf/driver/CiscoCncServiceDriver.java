package com.ibm.restconf.driver;

import com.ibm.restconf.model.ExecutionRequest;
import com.ibm.restconf.model.ResourceManagerDeploymentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Map;

import static com.ibm.restconf.config.RCDriverConstants.RC_SERVER_URL;

/**
 * Driver implementing the RestConf Management interface
 * <p>
 * Endpoints expected to be found under the following structure
 *
 * <ul>
 *     <li>{apiRoot}/crosswork
 *     <li><ul>
 *         <li>/sso/v1</li>
 *         <li><ul>
 *             <li>/ticket
 *             <li>/ticket/{ticket}</li>
 *         </ul></li>
 *     </ul></li>
 *     <li><ul>
 *         <li>/proxy/nso/restconf</li>
 *         <li><ul>
 *             <li>/data</li>
 *             <li><ul>
 *                 <li>/GET</li>
 *                 <li>/POST</li>
 *                 <li>/PUT</li>
 *                 <li>/DELETE</li>
 *             </ul></li>
 *         </ul></li>
 *     </ul></li>
 * </ul>
 */
@Service("CiscoCncAuthServiceDriver")
public class CiscoCncServiceDriver {

    private final static Logger logger = LoggerFactory.getLogger(CiscoCncServiceDriver.class);

    /**
     *  Following properties must come from DeploymentLocation object properties
     */
    private static final String API_CONTEXT = "apiContext";
    private static final String API_AUTH = "apiAuth";
    private static final String API_SLICES = "apiSlices";
    private static final String API_SLICE_FILTER_NAME = "apiSliceFilterName";
    private static final String API_UPGRADE_SUFFIX = "apiUpGradeSuffix";

    private static final String CONTENT_TYPE_YANG_XML = "application/yang-data+xml";
    private static final String CONTENT_TYPE_YANG_JSON = "application/yang-data+json";

    private final RestTemplate restTemplate;


    @Autowired
    public CiscoCncServiceDriver(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get a ticket from Cisco CNC server
     *\
     * <ul>
     *     <li>Collect a ticket from Cisco CNC</li>
     *     <li>Gets 201 Created response with a record as the response body</li>
     *     <li>Postcondition: Received Ticket</li>
     *     <li>Out of band  should be received after this returns</li>
     * </ul>
     *
     * @param deploymentLocation deployment location
     * @return newly created ticket
     * @throws CiscoCncResponseException if there are any errors getting the ticket
     */
    public String getTicket(final ResourceManagerDeploymentLocation deploymentLocation) throws CiscoCncResponseException{
        Map<String, Object> deploymentLocationProperties = deploymentLocation.getProperties();
        String apiContext = (String)deploymentLocationProperties.get(API_CONTEXT);
        String apiAuth = (String)deploymentLocationProperties.get(API_AUTH);
        final String url = deploymentLocation.getProperties().get(RC_SERVER_URL) + apiContext + apiAuth;

        String username = (String)deploymentLocationProperties.get("username");
        String password = (String)deploymentLocationProperties.get("password");

        final HttpHeaders headers = getHttpHeaders( null);
        headers.setContentType(MediaType.parseMediaType(MediaType.APPLICATION_FORM_URLENCODED_VALUE));
        headers.setAccept(Arrays.asList(MediaType.parseMediaType(MediaType.TEXT_PLAIN_VALUE)));
        StringBuilder builder = new StringBuilder();
        builder.append("username=");
        builder.append(username);
        builder.append("&");
        builder.append("password=");
        builder.append(password);
        String payload = builder.toString();

        final HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);

        final ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        checkResponseEntityMatches(responseEntity, HttpStatus.CREATED, true);
        return responseEntity.getBody();
    }

    /**
     * Get a JWT Token from Cisco CNC server
     *\
     * <ul>
     *     <li>Collect a JWT Token from Cisco CNC Server</li>
     *     <li>Gets 201 response with a record as the response body</li>
     *     <li>Postcondition: Receive JWT Token</li>
     *     <li>Out of band  should be received after this returns</li>
     * </ul>
     *
     * @param deploymentLocation deployment location
     * @param ticket ticket which will be received during getTicket() call
     * @return newly created JWT token
     * @throws CiscoCncResponseException if there are any errors getting the ticket
     */
    public String getToken(final ResourceManagerDeploymentLocation deploymentLocation, String ticket) throws CiscoCncResponseException{
        Map<String, Object> deploymentLocationProperties = deploymentLocation.getProperties();
        String apiContext = (String)deploymentLocationProperties.get(API_CONTEXT);
        String apiAuth = (String)deploymentLocationProperties.get(API_AUTH);
        final String url = deploymentLocation.getProperties().get(RC_SERVER_URL) + apiContext + apiAuth + "/" + ticket;

        final HttpHeaders headers = getHttpHeaders( null);
        headers.setContentType(MediaType.parseMediaType(MediaType.APPLICATION_FORM_URLENCODED_VALUE));
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        final HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        final ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        checkResponseEntityMatches(responseEntity, HttpStatus.CREATED, true);
        return responseEntity.getBody();
    }

    /**
     * Get Slices from Cisco CNC server
     *\
     * <ul>
     *     <li>Sends getSlices message via HTTP POST to /cisco-5g-transport-cfp:transport-slice to get all slices</li>
     *     <li>Sends getSlices message via HTTP POST to /cisco-5g-transport-cfp:transport-slice/dynamic=demo-slice2?content=config
     *     to get config slices</li>
     *     <li>Sends getSlices message via HTTP POST to /cisco-5g-transport-cfp:transport-slice/dynamic=demo-slice2?content=nonconfig
     *     to get nonconfig slices</li>
     *     <li>Gets 200 OK response with a record as the response body</li>
     *     <li>Postcondition: Brent will get the multiple type of slices based on internal filter</li>
     * </ul>
     *
     * @param deploymentLocation deployment location
     * @param jwt    JWT Token
     * @param sliceName type of slice API call
     * @param nonConfig request type whether call is for config slice or nonconfig. if it is null then get all slices
     * @return slices based on filter.
     * @throws CiscoCncResponseException if there are any errors getting the slices
     */
    public String getSlices(final ResourceManagerDeploymentLocation deploymentLocation, String jwt, String sliceName, boolean nonConfig) throws CiscoCncResponseException {
        Map<String, Object> deploymentLocationProperties = deploymentLocation.getProperties();
        String apiContext = (String)deploymentLocationProperties.get(API_CONTEXT);
        String apiSlices = (String)deploymentLocationProperties.get(API_SLICES);
        String apiSliceFilterName = (String)deploymentLocationProperties.get(API_SLICE_FILTER_NAME);
        String apiNonConfig = (String)deploymentLocationProperties.get("apiNonConfig");
        String apiConfig = (String)deploymentLocationProperties.get("apiConfig");

        String url = null;
        if (sliceName==null) {
            // gets all slices
            url = deploymentLocation.getProperties().get(RC_SERVER_URL) + apiContext + apiSlices;
        } else {
            if (nonConfig){
                // get slice non-config
                url = deploymentLocation.getProperties().get(RC_SERVER_URL) + apiContext + apiSlices + apiSliceFilterName + "=" + sliceName+"?"+apiNonConfig;
            } else {
                // get slice config
                url = deploymentLocation.getProperties().get(RC_SERVER_URL) + apiContext + apiSlices + apiSliceFilterName + "=" + sliceName+"?"+apiConfig;
            }
        }
        final HttpHeaders headers = getHttpHeaders(jwt);
        headers.setContentType(MediaType.TEXT_PLAIN);
        final HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        final ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        checkResponseEntityMatches(responseEntity, HttpStatus.OK, true);
        return responseEntity.getBody();
    }

    /**
     * Create a new slice
     *\
     * <ul>
     *     <li>Sends createSlice message via HTTP POST to /cisco-5g-transport-cfp:transport-slice to create a new slice</li>
     *     <li>Gets 201 Created response with a record as the response body</li>
     *     <li>Postcondition: New slice will be create.</li>
     * </ul>
     *
     * @param executionRequest ExecutionRquest
     * @param jwt    JWT Token
     * @param payload payload data
     * @return 201 created response.
     * @throws CiscoCncResponseException if there are any errors getting the slices
     */
    public String createSlice(final ExecutionRequest executionRequest, String jwt, String payload) throws CiscoCncResponseException{
        Map<String, Object> deploymentLocationProperties = executionRequest.getDeploymentLocation().getProperties();
        String apiContext = (String)deploymentLocationProperties.get(API_CONTEXT);
        String apiSlices = (String)deploymentLocationProperties.get(API_SLICES);
        final String url = deploymentLocationProperties.get(RC_SERVER_URL) + apiContext + apiSlices;
        logger.debug("url = {}", url);
        final HttpHeaders headers = getHttpHeaders(jwt);
        headers.setContentType(getContentType(executionRequest));
        headers.setAccept(Arrays.asList(MediaType.ALL));
        final HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);

        final ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        checkResponseEntityMatches(responseEntity, HttpStatus.CREATED, true);
        return responseEntity.getBody();
    }

    /**
     * UpGrade slice
     *\
     * <ul>
     *     <li>Sends upGradeSlice message via HTTP POST to /cisco-5g-transport-cfp:transport-slice/dynamic=demo-slice2/nsst to upGrade the slice</li>
     *     <li>Gets 200 OK upGraded response record as the response body</li>
     *     <li>Postcondition: Particular slice will be upGraded.</li>
     * </ul>
     *
     * @param executionRequest ExecutionRequest
     * @param jwt    JWT Token
     * @param payload Payload data
     * @return response 200 successful upGrade response.
     * @throws CiscoCncResponseException if there are any errors getting the slices
     */
    public String upGradeSlice(final ExecutionRequest executionRequest, String jwt, String sliceName, String payload) throws CiscoCncResponseException{
        ResourceManagerDeploymentLocation deploymentLocation = executionRequest.getDeploymentLocation();
        Map<String, Object> deploymentLocationProperties = deploymentLocation.getProperties();
        String apiContext = (String)deploymentLocationProperties.get(API_CONTEXT);
        String apiSlices = (String)deploymentLocationProperties.get(API_SLICES);
        String apiSliceFilterName = (String)deploymentLocationProperties.get(API_SLICE_FILTER_NAME);
        String apiUpGradeSuffix = (String)deploymentLocationProperties.get(API_UPGRADE_SUFFIX);

        final String url = deploymentLocation.getProperties().get(RC_SERVER_URL) + apiContext + apiSlices + apiSliceFilterName + "=" + sliceName + apiUpGradeSuffix;

        final HttpHeaders headers = getHttpHeaders(jwt);
        headers.setContentType(getContentType(executionRequest));
        headers.setAccept(Arrays.asList(MediaType.ALL));
        final HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);

        final ResponseEntity<String> responseEntity =restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);

        checkResponseEntityMatches(responseEntity, HttpStatus.OK, true);
        return responseEntity.getBody();
    }

    /**
     * Delete slice
     *\
     * <ul>
     *     <li>Sends deleteSlice message via HTTP POST to /cisco-5g-transport-cfp:transport-slice/dynamic=demo-slice2 to delete the slice</li>
     *     <li>204 NO_CONTENT response</li>
     *     <li>Postcondition: Particular slice will be deleted.</li>
     * </ul>
     *
     * @param executionRequest ExecutionRequest
     * @param jwt    JWT Token
     * @param sliceName Particular slice
     * @return response 204 No Content post successful deletion of particular slice.
     * @throws CiscoCncResponseException if there are any errors getting the slices
     */
    public String deleteSlice(final ExecutionRequest executionRequest, String jwt, String sliceName,String payload) throws CiscoCncResponseException{
        ResourceManagerDeploymentLocation deploymentLocation = executionRequest.getDeploymentLocation();
        Map<String, Object> deploymentLocationProperties = deploymentLocation.getProperties();
        String apiContext = (String)deploymentLocationProperties.get(API_CONTEXT);
        String apiSlices = (String)deploymentLocationProperties.get(API_SLICES);
        String apiSliceFilterName = (String)deploymentLocationProperties.get(API_SLICE_FILTER_NAME);
        final String url = deploymentLocation.getProperties().get(RC_SERVER_URL) +apiContext + apiSlices + apiSliceFilterName + "=" + sliceName;

        final HttpHeaders headers = getHttpHeaders(jwt);
        headers.setContentType(getContentType(executionRequest));
        headers.setAccept(Arrays.asList(MediaType.ALL));
        final HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);

        final ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
        checkResponseEntityMatches(responseEntity, HttpStatus.NO_CONTENT, false);
        return responseEntity.getBody();
    }

    /**
     * getContentType
     *\
     * <ul>
     *     <li>Checks ContentType property in Resource Properties, By default it will return 'application/yang-data+xml' </li>
     *     <li>Postcondition: It returns by default 'application/yang-data+xml', if set to json, returns 'application/yang-data+json'</li>
     * </ul>
     *
     * @param executionRequest ExecutionRequest
     * @return contentType Content Type
     */
    private MediaType getContentType(ExecutionRequest executionRequest){
        String requestContentType = CONTENT_TYPE_YANG_XML;
        Map<String, Object> resoureceProperties = executionRequest.getProperties();
        String contentType = (String)resoureceProperties.get("ContentType");
        if(!StringUtils.isEmpty(contentType) && contentType.equalsIgnoreCase("json")){
            requestContentType = CONTENT_TYPE_YANG_JSON;
        }
        return MediaType.parseMediaType(requestContentType);
    }

    /**
     * Creates HTTP headers, populating the content type (as application/yang-data+json)
     *
     */
    private HttpHeaders getHttpHeaders(String jwt) throws CiscoCncResponseException {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if(jwt!=null) {
            headers.setBearerAuth(jwt);
        }
        return headers;
    }

    /**
     * Utility method that checks if the HTTP status code matches the expected value and that it contains a response body (if desired)
     *
     * @param responseEntity       response to check
     * @param expectedStatusCode   HTTP status code to check against
     * @param containsResponseBody whether the response should contain a body
     */
    private void checkResponseEntityMatches(final ResponseEntity responseEntity, final HttpStatus expectedStatusCode, final boolean containsResponseBody) throws CiscoCncResponseException {
        // Check response code matches expected value (log a warning if incorrect 2xx status seen)
        if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getStatusCode() != expectedStatusCode) {
            // Be lenient on 2xx response codes
            logger.warn("Invalid status code [{}] received, was expecting [{}]", responseEntity.getStatusCode(), expectedStatusCode);
        } else if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new CiscoCncResponseException(String.format("Invalid status code [%s] received", responseEntity.getStatusCode()));
        }
        // Check if the response body is populated (or not) as expected
        if (containsResponseBody && responseEntity.getBody() == null) {
            throw new CiscoCncResponseException("No response body");
        } else if (!containsResponseBody && responseEntity.getBody() != null) {
            throw new CiscoCncResponseException("No response body expected");
        }
    }

}
