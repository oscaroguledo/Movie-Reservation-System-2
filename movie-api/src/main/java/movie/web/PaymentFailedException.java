package movie.web;

/** The submitted payment amount didn't match the reservation's price — payments are simulated. */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
