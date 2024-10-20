package com.booleanuk.OrderService.controllers;


import com.booleanuk.OrderService.models.Order;
import com.booleanuk.OrderService.repositories.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("orders")
public class OrderController {
    private SqsClient sqsClient;
    private SnsClient snsClient;
    private EventBridgeClient eventBridgeClient;
    private ObjectMapper objectMapper;
    private String queueUrl;
    private String topicArn;
    private String eventBusName;

    @Autowired
    private OrderRepository repository;

    public OrderController() {
        this.sqsClient = SqsClient.builder().build();
        this.snsClient = SnsClient.builder().build();
        this.eventBridgeClient = EventBridgeClient.builder().build();

        this.queueUrl = "https://sqs.eu-west-1.amazonaws.com/637423341661/FredrikEHOrderQueue";
        this.topicArn = "arn:aws:sns:eu-west-1:637423341661:FredrikEHOrderCreatedTopic";
        this.eventBusName = "arn:aws:events:eu-west-1:637423341661:event-bus/FredrikEHCustomEventBus";

        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = repository.findAll();
        processOrdersInBackground();
        return ResponseEntity.ok(orders);
    }

    @Async
    public void processOrdersInBackground() {
        while (true) {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(10)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            System.out.println("Received messages from the queue: " + messages.size());

            if (messages.isEmpty()) {
                break; // Exit loop if no more messages are available
            }

            for (Message message : messages) {
                try {
                    // Extract the "Message" field from the SNS notification
                    JsonNode messageNode = objectMapper.readTree(message.body());
                    String orderJson = messageNode.get("Message").asText();

                    System.out.println(orderJson);

                    // Deserialize the order JSON to an Order object
                    Order order = objectMapper.readValue(orderJson, Order.class);
                    processOrder(order);

                    // Delete message from the queue if order is processed
                    System.out.println("Order processed: " + order.isProcessed());
                    DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build();

                    sqsClient.deleteMessage(deleteRequest);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Processed orders in background: " + messages.size());
        }
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        Order newOrder = repository.save(order); // Save order to the database
        createdMessageInQueue(newOrder);
        return new ResponseEntity<>(newOrder, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable int id, @RequestBody Order orderDetails) {
        Optional<Order> optionalOrder = repository.findById(id);
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.setProduct(orderDetails.getProduct());
            order.setQuantity(orderDetails.getQuantity());
            order.setAmount(orderDetails.getAmount());
            order.setProcessed(orderDetails.isProcessed());
            Order updatedOrder = repository.save(order);
            createdMessageInQueue(updatedOrder);
            return ResponseEntity.ok(updatedOrder);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @Async
    public void createdMessageInQueue(Order order) {
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            System.out.println(orderJson);
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(orderJson)
                    .build();
            snsClient.publish(publishRequest);

            PutEventsRequestEntry eventEntry = PutEventsRequestEntry.builder()
                    .source("order.service")
                    .detailType("OrderCreated")
                    .detail(orderJson)
                    .eventBusName(eventBusName)
                    .build();

            PutEventsRequest putEventsRequest = PutEventsRequest.builder()
                    .entries(eventEntry)
                    .build();

            this.eventBridgeClient.putEvents(putEventsRequest);

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void processOrder(Order order) {
        order.calculateTotal();
        order.setProcessed(true);
        repository.save(order);
        System.out.println(order.toString());
    }
}