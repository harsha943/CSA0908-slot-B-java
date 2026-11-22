import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 1.2 - Repaired fee-payment window.
 * The window only collects input and shows results; FeeService validates and pays.
 * Each button has its own listener; slow work runs off the event-dispatch thread.
 */
class FeeService {
    record Receipt(String regNo, double amount, long receiptNo) {}

    private final List<Receipt> paid = new ArrayList<>();
    private long nextReceipt = 5001;

    /** Validates input; returns an error message, or null if valid. */
    String validate(String regNo, String amountText) {
        if (regNo == null || !regNo.trim().matches("\\d{9}")) return "Register number must be 9 digits";
        try {
            double a = Double.parseDouble(amountText.trim());
            if (a <= 0) return "Amount must be positive";
            if (a > 500_000) return "Amount exceeds limit";
        } catch (NumberFormatException e) {
            return "Amount must be a number";
        }
        return null;
    }

    /**
     * Records the payment. In the real system this uses a PreparedStatement
     * (INSERT INTO fees(reg_no, amount) VALUES (?, ?)) inside try-with-resources.
     */
    synchronized Receipt pay(String regNo, double amount) throws InterruptedException {
        Thread.sleep(1500);                        // simulated bank confirmation (NOT on the EDT)
        Receipt r = new Receipt(regNo.trim(), amount, nextReceipt++);
        paid.add(r);
        return r;
    }

    synchronized Receipt lastReceipt(String regNo) {
        for (int i = paid.size() - 1; i >= 0; i--) if (paid.get(i).regNo().equals(regNo.trim())) return paid.get(i);
        return null;
    }
}

public class FeeWindow extends Frame {
    private final TextField regNo = new TextField(12);
    private final TextField amount = new TextField(10);
    private final Button pay = new Button("Pay");
    private final Button clear = new Button("Clear");
    private final Button receipt = new Button("Receipt");
    private final Label status = new Label("Enter register number and amount", Label.LEFT);
    private final FeeService service;

    public FeeWindow(FeeService service) {
        super("Student Fee Payment");
        this.service = service;
        setLayout(new BorderLayout(5, 5));

        Panel form = new Panel(new GridLayout(2, 2, 5, 5));
        form.add(new Label("Register No:")); form.add(regNo);
        form.add(new Label("Amount (Rs):")); form.add(amount);
        Panel buttons = new Panel(new FlowLayout());
        buttons.add(pay); buttons.add(clear); buttons.add(receipt);

        add(form, BorderLayout.NORTH);
        add(buttons, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        pay.addActionListener(e -> onPay());
        clear.addActionListener(e -> { regNo.setText(""); amount.setText(""); show("Cleared"); });
        receipt.addActionListener(e -> onReceipt());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); }
        });
        setSize(380, 180);
    }

    private void onPay() {
        String error = service.validate(regNo.getText(), amount.getText());
        if (error != null) { show("Error: " + error); return; }
        double a = Double.parseDouble(amount.getText().trim());
        pay.setEnabled(false);
        show("Processing payment...");
        new Thread(() -> {                                    // keep the window responsive
            try {
                FeeService.Receipt r = service.pay(regNo.getText(), a);
                EventQueue.invokeLater(() -> show("Paid Rs " + r.amount() + ", receipt #" + r.receiptNo()));
            } catch (InterruptedException ex) {
                EventQueue.invokeLater(() -> show("Payment interrupted"));
            } finally {
                EventQueue.invokeLater(() -> pay.setEnabled(true));
            }
        }).start();
    }

    private void onReceipt() {
        FeeService.Receipt r = service.lastReceipt(regNo.getText());
        show(r == null ? "No payment found for this register number"
                : "Receipt #" + r.receiptNo() + ": " + r.regNo() + " paid Rs " + r.amount());
    }

    private void show(String msg) { status.setText(msg); }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> new FeeWindow(new FeeService()).setVisible(true));
    }
}
