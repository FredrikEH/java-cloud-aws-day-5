package com.booleanuk.OrderService.clients;

import software.amazon.awssdk.annotations.Generated;
import software.amazon.awssdk.annotations.ThreadSafe;
import software.amazon.awssdk.awscore.AwsClient;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.model.*;

import java.lang.UnsupportedOperationException;

@Generated("software.amazon.awssdk:codegen")
@ThreadSafe
public interface SqsClient extends AwsClient {
    public DeleteMessageResponse deleteMessage(DeleteMessageRequest deleteMessageRequest)
            throws  InvalidIdFormatException,
                    ReceiptHandleIsInvalidException,
                    RequestThrottledException,
                    QueueDoesNotExistException,
                    UnsupportedOperationException,
                    InvalidSecurityException,
                    InvalidAddressException,
                    AwsServiceException,
                    SdkClientException,
                    SqsException;
}
