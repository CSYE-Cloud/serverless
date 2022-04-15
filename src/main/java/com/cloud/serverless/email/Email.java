package com.cloud.serverless.email;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;

public class Email implements RequestHandler<SNSEvent, String> {

	 private DynamoDB dynamoDB;
	    private static String EMAIL_SUBJECT;
	    private static final String EMAIL_SENDER = "verify-email@demo.garimachoudhary.me";

    @Override
    public String handleRequest(SNSEvent request, Context context) {
        context.getLogger().log("Received event: " + request);
        String message = request.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log("From SNS: " + message);
 
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        dynamoDB = new DynamoDB(client);
        Table tableA = dynamoDB.getTable("AccountEmail");
        Table tableB = dynamoDB.getTable("AccountDetails");
        if(tableA == null) {
            context.getLogger().log("Table 'AccountEmail' is not in dynamoDB.");
            return null;
        } else if (request.getRecords() == null) {
            context.getLogger().log("There are currently no records in the SNS Event.");
            return null;
        }
        System.out.println("records in table");
        
        
        if(tableB == null) {
            context.getLogger().log("Table 'AccountDetails' is not in dynamoDB.");
            return null;
        } 

        // get SNS message
        String messageSNS =  request.getRecords().get(0).getSNS().getMessage();
        List<String> messageInfo = Arrays.asList(messageSNS.split("\\|"));
        StringBuilder messageSB = new StringBuilder();
        
        String userEmail = messageInfo.get(1);
        
        String linktoSendUser="http://demo.garimachoudhary.me/v1/verifyUserEmail?email="+userEmail+"&token="+messageInfo.get(2);
        
        messageSB.append("Hi, Username: ").append(userEmail).append("\n");
        if (messageInfo.get(0).equals("POST")) {
            messageSB.append("Click on this link to verify username ").append(linktoSendUser);
            messageSB.insert(0, "Verify.\n\n");
            EMAIL_SUBJECT = "Verify your user account";
        } else {
            messageSB.insert(0, "POST no request.\n\n");
            EMAIL_SUBJECT = "Post not request";
        }
        
        // send email if no duplicate in dynamoDB
        String emailMessage = messageSB.toString();
//        logger.info(emailMessage);
        Item item = tableA.getItem("id",userEmail);
        System.out.println("item= "+item);
        if (item == null) {
            //table.putItem(new PutItemSpec().withItem(new Item().withString("id", emailMessage)));
            
        	PutItemOutcome outcome  = tableA
            .putItem(new Item().withPrimaryKey("id", userEmail).with("emailMessage", emailMessage));
            
            System.out.println("PutItem succeeded:\n" + outcome.getPutItemResult());
            
            //System.currentTimeMillis() / 1000L;
            
            long now = Instant.now().getEpochSecond(); // unix time
            long ttl = 60 * 2; // 2 mins in sec
            ttl=(ttl + now); // when object will be expired
            
            outcome  = tableB
                    .putItem(new Item().withPrimaryKey("id", userEmail).with("TimeToExist", ttl).with("OneTimeToken",messageInfo.get(2)));
                   
            
                    System.out.println("PutItem in second succeeded:  \n" + outcome.getPutItemResult());
                    
                    System.out.println("TTL is: "+ttl);
                    
            
            
            
            
//            final UpdateTimeToLiveRequest req = new UpdateTimeToLiveRequest();
//            req.setTableName("Emails_Sent");
//
//            final TimeToLiveSpecification ttlSpec = new TimeToLiveSpecification();
//            ttlSpec.setAttributeName(ttlField);
//            ttlSpec.setEnabled(true);
//            req.withTimeToLiveSpecification(ttlSpec);
//
//            client.updateTimeToLive(req);
            
            
            
            Content content = new Content().withData(emailMessage).withCharset("UTF-8");
            Body emailBody = new Body().withText(content);
            try {
                AmazonSimpleEmailService emailService =
                        AmazonSimpleEmailServiceClientBuilder.defaultClient();
                SendEmailRequest emailRequest = new SendEmailRequest()
                        .withDestination(new Destination().withToAddresses(messageInfo.get(1)))
                        .withMessage(new Message()
                                .withBody(emailBody)
                                .withSubject(new Content().withCharset("UTF-8").withData(EMAIL_SUBJECT)))
                        .withSource(EMAIL_SENDER);
                emailService.sendEmail(emailRequest);
                context.getLogger().log("Sent email!");
               System.out.println("email sent");
            } catch (Exception ex) {
            	System.out.println("error in sending email");
                context.getLogger().log(ex.getLocalizedMessage());
            }
        }
        else{
        	System.out.println("email already send");
        }

        return null;
    }
}
