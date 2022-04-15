package com.cloud.serverless.serverless;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
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

    //@DynamoDBAttribute
    //private long ttl;
//
    private DynamoDB dynamoDB;
    //private DynamoDB db2;
    private static String EMAIL_SUBJECT;
    private static final String EMAIL_SENDER = "verify-email@demo.garimachoudhary.me";

    @Override
    public String handleRequest(SNSEvent request, Context context) {
        context.getLogger().log("Received event: " + request.toString());
        context.getLogger().log("Received context: " + context.toString());
        context.getLogger().log("Received dd: " +  request.getRecords().toString());
        context.getLogger().log("Received fff: " +  request.getRecords().get(0).toString());

        String message = request.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log("From SNS: " + message);
        //       return message;
//    }


//    public Object handleRequest(SNSEvent request, Context context){

        // confirm dynamoDB table exists
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        dynamoDB = new DynamoDB(client);
        // db2=new DynamoDB()
        Table table = dynamoDB.getTable("AccountEmail");
        Table userEmailsTable = dynamoDB.getTable("AccountDetails");
        if(table == null) {
            context.getLogger().log("Table 'Emails_Sent' is not in dynamoDB.");
            return null;
        } else if (request.getRecords() == null) {
            context.getLogger().log("There are currently no records in the SNS Event.");
            return null;
        }
        System.out.println("records in table");


        if(userEmailsTable == null) {
            context.getLogger().log("Table 'EmailID_Data' is not in dynamoDB.");
            return null;
        }

        // get SNS message
        String msgSNS =  request.getRecords().get(0).getSNS().getMessage();
        // requestType, recipientEmail, bookId, bookName, author, link
        List<String> msgInfo = Arrays.asList(msgSNS.split("\\|"));
        StringBuilder emailMsgSB = new StringBuilder();
        StringBuilder url = new StringBuilder();
        url.append(msgInfo.get(2));
        String userEmail = msgInfo.get(1);
        //create token
        String linktoSendUser="http://demo.garimachoudhary.me/v1/verifyUserEmail?email="+userEmail+"&token="+msgInfo.get(2);
        //append to url
        //url.append(token);
        emailMsgSB.append("Hi, Username: ").append(userEmail).append("\n");
        if (msgInfo.get(0).equals("POST")) {
            emailMsgSB.append("Click on this link to verify username ").append(linktoSendUser);
            emailMsgSB.insert(0, "Verify.\n\n");
            EMAIL_SUBJECT = "Verify your user account";
        } else {
            emailMsgSB.insert(0, "POST no request.\n\n");
            EMAIL_SUBJECT = "Post not request";
        }

        // send email if no duplicate in dynamoDB
        String emailMsg = emailMsgSB.toString();
        Item item = table.getItem("emailID",userEmail);
        System.out.println("item= "+item);
        if (item == null) {

            PutItemOutcome outcome  = table
                    .putItem(new Item().withPrimaryKey("emailID", userEmail).with("emailmsg", emailMsg));

            System.out.println("PutItem succeeded:\n" + outcome.getPutItemResult());

            //System.currentTimeMillis() / 1000L;

            long now = Instant.now().getEpochSecond(); // unix time
            long ttl = 60 * 2; // 2 mins in sec
            ttl=(ttl + now); // when object will be expired

            for(int i=0; i<msgInfo.size(); i++){
                context.getLogger().log(msgInfo.get(i));

            }

            outcome  = userEmailsTable
                    .putItem(new Item().withPrimaryKey("emailID", userEmail).with("TimeToLive", ttl).with("Token",msgInfo.get(2)));


            System.out.println("PutItem in second succeeded:  \n" + outcome.getPutItemResult());

            System.out.println("TTL is: "+ttl);

            Content content = new Content().withData(emailMsg);
            Body emailBody = new Body().withText(content);
            try {
                AmazonSimpleEmailService emailService =
                        AmazonSimpleEmailServiceClientBuilder.defaultClient();
                SendEmailRequest emailRequest = new SendEmailRequest()
                        .withDestination(new Destination().withToAddresses(msgInfo.get(1)))
                        .withMessage(new Message()
                                .withBody(emailBody)
                                .withSubject(new Content().withCharset("UTF-8").withData(EMAIL_SUBJECT)))
                        .withSource(EMAIL_SENDER);
                emailService.sendEmail(emailRequest);
                context.getLogger().log("Sent email!");
                System.out.println("email sent");
            } catch (Exception ex) {
                System.out.println("eroor in sending email");
                context.getLogger().log(ex.getLocalizedMessage());
            }
        }
        else{
            System.out.println("email already send");
        }

        return null;
    }
}
