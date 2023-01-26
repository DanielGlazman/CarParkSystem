package CarPark.server;

import CarPark.entities.*;
import CarPark.entities.messages.*;
import CarPark.server.handlers.*;
import CarPark.server.ocsf.AbstractServer;
import CarPark.server.ocsf.ConnectionToClient;
import CarPark.server.ocsf.SubscribedClient;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;


public class SimpleServer extends AbstractServer {
    private static ArrayList<SubscribedClient> SubscribersList = new ArrayList<>();
    public static Session session;// encapsulation make public function so this can be private
    private OrderReminderThread orderReminderThread;
    private MembershipReminderThread membershipReminderThread;
    private StatisticsThread statisticsThread;

    public SimpleServer(int port) {
        super(port);
//        OrderReminderThread orderReminderThread = new OrderReminderThread();
//        orderReminderThread.start();
//        MembershipReminderThread membershipReminderThread = new MembershipReminderThread();
//        membershipReminderThread.start();
        StatisticsThread statisticsThread = new StatisticsThread();
        statisticsThread.start();
    }


    private static SessionFactory getSessionFactory() throws HibernateException {
        Configuration configuration = new Configuration();

        // Add ALL of your entities here. You can also try adding a whole package.
        configuration.addAnnotatedClass(Parkinglot.class);
        configuration.addAnnotatedClass(Price.class);
        configuration.addAnnotatedClass(Order.class);
        configuration.addAnnotatedClass(User.class);
        configuration.addAnnotatedClass(Employee.class);
        configuration.addAnnotatedClass(Customer.class);
        configuration.addAnnotatedClass(Membership.class);
        configuration.addAnnotatedClass(Complaint.class);
        configuration.addAnnotatedClass(ParkingSlot.class);
        configuration.addAnnotatedClass(Statistics.class);
        configuration.addAnnotatedClass(CheckedIn.class);
       // configuration.addAnnotatedClass(ParkingLotWorker.class);

        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build();        //pull session factory config from hibernate properties
        return configuration.buildSessionFactory(serviceRegistry);
    }

    @Override
    protected void handleMessageFromClient(Object msg, ConnectionToClient client) throws IOException, SQLException {
        try {
            MessageHandler handler = null;
            Class<?> msgClass = msg.getClass();
            if (ConnectionMessage.class.equals(msgClass)) { //New client connection
                SubscribedClient connection = new SubscribedClient(client);
                SubscribersList.add(connection);
                session = getSessionFactory().openSession();// Create new session for connection
            } else { //Get client requests
                session.beginTransaction();
                if (LoginMessage.class.equals(msgClass)) {
                    handler = new LoginHandler((LoginMessage) msg, session, client);
                } else if (ParkingListMessage.class.equals(msgClass)) {
                    handler = new ParkingListHandler((ParkingListMessage) msg, session, client);
                } else if (PricesMessage.class.equals(msgClass)) {
                    handler = new PricesTableHandler((PricesMessage) msg, session, client);
                } else if (OrderMessage.class.equals(msgClass)) {
                    handler = new OrderHandler((OrderMessage) msg, session, client);
                } else if (MembershipMessage.class.equals(msgClass)) {
                    handler = new MembershipsHandler((MembershipMessage) msg, session, client);
                } else if (ParkingLotMapMessage.class.equals(msgClass)) {
                    handler = new ParkingLotMapHandler((ParkingLotMapMessage) msg, session, client);
                } else if (CheckOutMessage.class.equals(msgClass)) {
                    handler = new CheckOutHandler((CheckOutMessage) msg, session, client);
                } else if (RegisterUserMessage.class.equals(msgClass)){
                    handler = new RegisterUserHandler((RegisterUserMessage)msg,session,client);
                } else if (RegisterUserMessage.class.equals(msgClass)) {
                    handler = new RegisterUserHandler((RegisterUserMessage) msg, session, client);
                } else if (Statistics.class.equals(msgClass)) {
                    handler = new StatisticsHandler((StatisticsMessage) msg, session, client);
                }
                if (handler != null) {
                    handler.handleMessage();
                    session.getTransaction().commit();
                    handler.message.message_type = Message.MessageType.RESPONSE;
                    client.sendToClient(handler.message);
                }
            }
        } catch (Exception exception) {
            if (session != null)
                session.getTransaction().rollback();
            exception.printStackTrace();
        }
    }

    public static class OrderReminderThread extends Thread {
        @Override
        public void run() {
            var session = getSessionFactory().openSession();
            while (true) {
//              get all orders which their arrival time was between now and 5 minutes ago and orderStatus is APPROVED
                var orders = session.createQuery("from Order where orderStatus = 'APPROVED' and arrivalTime between :five_minutes_ago and :now")
                        .setParameter("now", LocalDateTime.now())
                        .setParameter("five_minutes_ago", LocalDateTime.now().minusMinutes(5))
                        .getResultList();
                for (Object order : orders) {
                    String email = ((Order) order).getEmail();
                    String subject = "Did you forget your order?";
                    String text = "Hi, \nYou have an order that you haven't checked in yet and we would " +
                            "like to remind you that in case you are late or don't show up you will be charged according to the terms and conditions of the parking lot.\n\nBest regards,\nCarPark";
                    EmailSender.sendEmail(email, subject, text);
                }
//                change the status of the orders to be NOTIFIED
                session.beginTransaction();
                for (Object order : orders) {
//                    update the order status to be NOTIFIED
                    session.createQuery("update Order set orderStatus = :status where id = :id")
                            .setParameter("status", Order.Status.NOTIFIED)
                            .setParameter("id", ((Order) order).getId())
                            .executeUpdate();
                    ((Order) order).setOrderStatus(Order.Status.NOTIFIED);
                    session.update(order);
                }
                session.getTransaction().commit();
                try {
                    Thread.sleep(120000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class StatisticsThread extends Thread {
        @Override
        public void run() {
            var session = getSessionFactory().openSession();
            while (true) {
                    var parkingLots = session.createQuery("from Parkinglot").list();
                    for (Object parkingLot : parkingLots) {
//                        check if there is an entry for yesterday
                        var yesterday = LocalDate.now().minusDays(1);
                        var yesterdayStatistics = session.createQuery("from Statistics where parkingLotId = :parkingLotId and date = :date")
                                .setParameter("parkingLotId", ((Parkinglot) parkingLot).getId())
                                .setParameter("date", yesterday)
                                .getResultList();
                        if (yesterdayStatistics.size() == 0) {
                            String parkingLotId = String.valueOf(((Parkinglot) parkingLot).getParkingLotId());
                            //                        select all orders from the begiining of yesterday to the end of yesterday
//                            wrap yesterday in a LocalDateTime object
                            LocalDateTime yesterdayStart = LocalDateTime.of(yesterday, LocalTime.MIN);
                            LocalDateTime yesterdayEnd = LocalDateTime.of(yesterday, LocalTime.MAX);
                            var orders = session.createQuery("from Order where parkingLot = :parkingLotId and arrivalTime between :yesterday_start and :yesterday_end")
                                    .setParameter("parkingLotId", parkingLotId)
                                    .setParameter("yesterday_start", yesterdayStart)
                                    .setParameter("yesterday_end", yesterdayEnd)
                                    .getResultList();
                            int totalOrders = orders.size();
                            int numberOfOrdersCancelled = 0;
                            int numberOfOrdersLate = 0;
                            int totalRevenue = 0;
                            for (Object order : orders) {
                                totalRevenue += ((Order) order).getOrdersPrice();
                                switch (((Order) order).getStatus()) {
                                    case APPROVED:
                                        break;
                                    case NOTIFIED:
                                        numberOfOrdersLate++;
                                        break;
                                    case CANCELLED:
                                        numberOfOrdersCancelled++;
                                        break;
                                }
                            }
                            Statistics statistics = new Statistics(yesterday, totalOrders, numberOfOrdersCancelled, numberOfOrdersLate, parkingLotId, totalRevenue);
                            session.beginTransaction();
                            session.save(statistics);
                            System.out.println("Statistics for parking lot " + parkingLotId + " for yesterday were saved"+statistics);
                            session.getTransaction().commit();
                        }
                    }
                try {
                    Thread.sleep(86400000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class MembershipReminderThread extends Thread {
        @Override
        public void run() {
            while (true) {
                var session = getSessionFactory().openSession();
//                get all members whose membership is about to expire between now and in 7 days
                var memberships = session.createQuery("from Membership where endDate between :now and :seven_days_from_now")
                        .setParameter("now", LocalDateTime.now())
                        .setParameter("seven_days_from_now", LocalDateTime.now().plusDays(7))
                        .getResultList();
                System.out.println(memberships.size() + " memberships are about to expire");
//              for each membership get the customerId attribute and get the list of Customer objects having that id
                for (Object membership : memberships) {
//              get the customer object that has the same customerId as the membership
                    var customer = session.createQuery("from Customer where userId = :id")
                            .setParameter("id", ((Membership) membership).getCustomerId())
                            .list();
//                  get the email from the customer object
                    String email = ((Customer) customer.get(0)).getEmail();
                    String subject = "Your membership is about to expire";
//                    send a text with the expiration date
                    String text = "Hi, \nWe'd like to inform you that Your membership is about to expire on " + ((Membership) membership).getEndDate() + "\nYou can login to your" +
                            "acount in order to renew it :)"+"\n\nBest regards,\nCarParkSystem";
                    EmailSender.sendEmail(email, subject, text);
                }
                // now wait for 7 days
                try {
                    Thread.sleep(604000000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class EmailSender {
        public static void sendEmail(String to, String subject, String text) {
            String from = "ModernParkingSolutionsCPS@outlook.com";

            Properties properties = new Properties();
            properties.put("mail.smtp.host", "smtp-mail.outlook.com");
            properties.put("mail.transport.protocol", "smtp");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.port", "587");
            properties.put("mail.debug", "true");
            properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
            javax.mail.Session mailSession = javax.mail.Session.getDefaultInstance(properties, null);

            try {
                javax.mail.Message msg = new MimeMessage(mailSession);
                msg.setFrom(new InternetAddress(from));
                msg.setRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
                msg.setSubject(subject);
                msg.setText(text);

                // Send the msg to the recipient.
                Transport.send(msg, "ModernParkingSolutionsCPS@outlook.com", "cpsteam4");
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        }
    }

}
