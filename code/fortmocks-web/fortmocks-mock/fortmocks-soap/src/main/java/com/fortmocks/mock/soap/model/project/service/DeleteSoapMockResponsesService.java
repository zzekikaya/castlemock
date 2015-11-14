package com.fortmocks.mock.soap.model.project.service;

import com.fortmocks.core.model.Service;
import com.fortmocks.core.model.Result;
import com.fortmocks.core.model.Task;
import com.fortmocks.mock.soap.model.project.domain.SoapMockResponse;
import com.fortmocks.mock.soap.model.project.domain.SoapOperation;
import com.fortmocks.mock.soap.model.project.dto.SoapMockResponseDto;
import com.fortmocks.mock.soap.model.project.service.message.input.DeleteSoapMockResponsesInput;
import com.fortmocks.mock.soap.model.project.service.message.output.DeleteSoapMockResponsesOutput;

/**
 * @author Karl Dahlgren
 * @since 1.0
 */
@org.springframework.stereotype.Service
public class DeleteSoapMockResponsesService extends AbstractSoapProjectProcessor implements Service<DeleteSoapMockResponsesInput, DeleteSoapMockResponsesOutput> {

    /**
     * The process message is responsible for processing an incoming task and generate
     * a response based on the incoming task input
     * @param task The task that will be processed by the service
     * @return A result based on the processed incoming task
     * @see Task
     * @see Result
     */
    @Override
    public Result<DeleteSoapMockResponsesOutput> process(final Task<DeleteSoapMockResponsesInput> task) {
        final DeleteSoapMockResponsesInput input = task.getInput();
        final SoapOperation soapOperation = findSoapOperationType(input.getSoapProjectId(), input.getSoapPortId(), input.getSoapOperationId());
        for(SoapMockResponseDto soapMockResponseDto : input.getMockResponses()){
            final SoapMockResponse soapMockResponse = new SoapMockResponse();
            soapMockResponse.setId(soapMockResponseDto.getId());
            soapOperation.getSoapMockResponses().remove(soapMockResponse);
        }

        save(input.getSoapProjectId());
        return createResult(new DeleteSoapMockResponsesOutput());
    }
}