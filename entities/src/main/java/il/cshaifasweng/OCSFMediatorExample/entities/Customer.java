package il.cshaifasweng.OCSFMediatorExample.entities;
import javax.persistence.*;

@Entity
@Table(name = "Customers")

public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private long customerId;
    private String firstName;
    private String lastName;
    private String email;
    private int balance;


    public Customer(int customerId, String firstName, String lastName, String email, int balance)
    {
        super();
        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.balance = balance;
    }

    public Customer() {}

    public long getCustomerId()
    {
        return customerId;
    }
    public void setCustomerId(int customerId)
    {
        this.customerId = customerId;
    }

    public String getFirstName()
    {
        return firstName;
    }
    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getLastName()
    {
        return lastName;
    }
    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

    public String getEmail()
    {
        return email;
    }
    public void setEmail(String email)
    {
        this.email = email;
    }

    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }

    public int getId()
    {
        return id;
    }
}