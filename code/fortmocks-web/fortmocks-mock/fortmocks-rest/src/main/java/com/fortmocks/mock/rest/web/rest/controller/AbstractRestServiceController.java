package com.fortmocks.mock.rest.web.rest.controller;

import com.fortmocks.mock.rest.model.RestException;
import com.fortmocks.mock.rest.model.event.dto.RestEventDto;
import com.fortmocks.mock.rest.model.event.dto.RestRequestDto;
import com.fortmocks.mock.rest.model.event.dto.RestResponseDto;
import com.fortmocks.mock.rest.model.event.service.RestEventService;
import com.fortmocks.mock.rest.model.project.domain.RestMethodStatus;
import com.fortmocks.mock.rest.model.project.domain.RestMethodType;
import com.fortmocks.mock.rest.model.project.domain.RestMockResponseStatus;
import com.fortmocks.mock.rest.model.project.domain.RestResponseStrategy;
import com.fortmocks.mock.rest.model.project.dto.RestMethodDto;
import com.fortmocks.mock.rest.model.project.dto.RestMockResponseDto;
import com.fortmocks.mock.rest.model.project.service.RestProjectService;
import com.fortmocks.web.core.web.mvc.controller.AbstractController;
import com.google.common.base.Preconditions;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author Karl Dahlgren
 * @since 13.4
 */
public abstract class AbstractRestServiceController extends AbstractController {

    @Autowired
    private RestProjectService restProjectService;
    @Autowired
    private RestEventService restEventService;
    private static final String REST = "rest";
    private static final String APPLICATION = "application";
    private static final Random RANDOM = new Random();


    /**
     *
     * @param projectId The id of the project which the incoming request and mocked response belongs to
     * @param restMethodType The request method
     * @param httpServletRequest The incoming request
     * @param httpServletResponse The outgoing response
     * @return Returns the response as an String
     */
    protected String process(final Long projectId, final Long applicationId, final RestMethodType restMethodType, final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse){
        Preconditions.checkNotNull(projectId, "The project id cannot be null");
        Preconditions.checkNotNull(applicationId, "The application id cannot be null");
        Preconditions.checkNotNull(restMethodType, "The REST method cannot be null");
        Preconditions.checkNotNull(httpServletRequest, "The HTTP Servlet Request cannot be null");
        Preconditions.checkNotNull(httpServletResponse, "The HTTP Servlet Response cannot be null");

        final RestRequestDto restRequest = prepareRequest(projectId, applicationId, restMethodType, httpServletRequest);
        final RestMethodDto restMethod = restProjectService.findRestMethod(projectId, applicationId, restRequest.getUri(), restMethodType);

        if(restMethod == null){
            throw new RestException("Unable to locate the REST method");
        }

        return process(restRequest, restMethod, httpServletResponse);
    }

    /**
     * The method prepares an request
     * @param projectId The id of the project that the incoming request belongs to
     * @param httpServletRequest The incoming request
     * @return A new created project
     */
    protected RestRequestDto prepareRequest(final Long projectId, final Long applicationId, final RestMethodType restMethodType, final HttpServletRequest httpServletRequest) {
        final RestRequestDto request = new RestRequestDto();
        final String body = RestMessageSupport.getBody(httpServletRequest);
        final String restResourceUri = httpServletRequest.getRequestURI().toLowerCase().replace(getContext() + SLASH + MOCK + SLASH + REST + SLASH + PROJECT + SLASH + projectId + SLASH + APPLICATION + SLASH + applicationId, EMPTY);

        request.setContextType(httpServletRequest.getContentType());
        request.setBody(body);
        request.setUri(restResourceUri);
        return request;
    }


    /**
     * The process method provides the functionality to process an incoming request. The request will be identified
     * and a corresponding action will be applied for the request. The following actions are support:
     * Forward, record, mock or disable.
     * @param restRequest The incoming request
     * @param restMethod The REST method which the incoming request belongs to
     * @param httpServletResponse The HTTP servlet response
     * @return A response in String format
     */
    protected String process(final RestRequestDto restRequest, final RestMethodDto restMethod, final HttpServletResponse httpServletResponse){
        Preconditions.checkNotNull(restRequest, "Rest request cannot be null");
        RestEventDto event = null;
        RestResponseDto response = null;
        try {
            event = new RestEventDto(restRequest, restMethod.getId());
            if (RestMethodStatus.DISABLED.equals(restMethod.getRestMethodStatus())) {
                throw new RestException("The requested REST method, " + restMethod.getName() + ", is disabled");
            } else if (RestMethodStatus.FORWARDED.equals(restMethod.getRestMethodStatus())) {
                response = forwardRequest(restRequest, restMethod);
            } else if (RestMethodStatus.RECORDING.equals(restMethod.getRestMethodStatus())) {
                response = forwardRequestAndRecordResponse(restRequest, restMethod);
            } else { // Status.MOCKED
                response = mockResponse(restMethod, httpServletResponse);
            }
            return response.getBody();
        } finally{
            if(event != null){
                event.finish(response);
                restEventService.save(event);
            }
        }
    }

    /**
     * The method provides the functionality to forward a request to another endpoint
     * @param restRequest The incoming request
     * @param restMethod The REST method which the incoming request belongs to
     * @return The response received from the external endpoint
     */
    protected RestResponseDto forwardRequest(final RestRequestDto restRequest, final RestMethodDto restMethod){
        return null;
    }

    /**
     * The method provides the functionality to forward a request to another endpoint. The response
     * will be recorded and can later be used as a mocked response
     * @param restRequest The incoming request
     * @param restMethod The REST method which the incoming request belongs to
     * @return The response received from the external endpoint
     */
    protected RestResponseDto forwardRequestAndRecordResponse(final RestRequestDto restRequest, final RestMethodDto restMethod){
        return null;
    }

    /**
     * The method is responsible for mocking a REST service. The method will identify which mocked response
     * will be returned.
     * @param restMethod The REST method which the incoming request belongs to
     * @param httpServletResponse The HTTP servlet response
     * @return Returns a selected mocked response which will be returned to the service consumer
     */
    protected RestResponseDto mockResponse(final RestMethodDto restMethod, final HttpServletResponse httpServletResponse){
        final List<RestMockResponseDto> mockResponses = new ArrayList<RestMockResponseDto>();
        for(RestMockResponseDto mockResponse : restMethod.getRestMockResponses()){
            if(mockResponse.getRestMockResponseStatus().equals(RestMockResponseStatus.ENABLED)){
                mockResponses.add(mockResponse);
            }
        }

        RestMockResponseDto mockResponse = null;
        if(mockResponses.isEmpty()){
            throw new RestException("No mocked response created for operation " + restMethod.getName());
        } else if(restMethod.getRestResponseStrategy().equals(RestResponseStrategy.RANDOM)){
            final Integer responseIndex = RANDOM.nextInt(mockResponses.size());
            mockResponse = mockResponses.get(responseIndex);
        } else if(restMethod.getRestResponseStrategy().equals(RestResponseStrategy.SEQUENCE)){
            Integer currentSequenceNumber = restMethod.getCurrentResponseSequenceIndex();
            if(currentSequenceNumber >= mockResponses.size()){
                currentSequenceNumber = 0;
            }
            mockResponse = mockResponses.get(currentSequenceNumber);
            restProjectService.updateCurrentResponseSequenceIndex(restMethod.getId(), currentSequenceNumber + 1);
        }

        if(mockResponse == null){
            throw new RestException("No mocked response created for operation " + restMethod.getName());
        }

        final RestResponseDto response = new RestResponseDto();
        response.setBody(mockResponse.getBody());
        response.setMockResponseName(mockResponse.getName());
        httpServletResponse.setStatus(mockResponse.getHttpStatusCode());
        httpServletResponse.setContentType(mockResponse.getRestContentType().getContentType());
        httpServletResponse.setHeader("Content-type", mockResponse.getRestContentType().getContentType());
        return response;
    }
}