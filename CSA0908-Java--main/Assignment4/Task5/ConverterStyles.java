import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Task 5.1 / 5.2 - The same converter written in four event-handling styles.
 * Changed requirement (5.2) is already applied: Enter in the amount field also converts,
 * and the result is shown in a dialog box.
 * Usage: java ConverterStyles a|b|c|d   (default: opens all four)
 */
public class ConverterStyles {

    /** Shared, non-GUI logic. */
    static String convert(String text, String cur) {
        try {
            double inr = Double.parseDouble(text.trim());
            if (inr < 0) return "Invalid amount";
            double rate = cur.equals("USD") ? 0.012 : 0.011;
            return String.format("%.2f INR = %.2f %s", inr, inr * rate, cur);
        } catch (NumberFormatException e) {
            return "Invalid amount";
        }
    }

    /** Base frame with the common components; WindowAdapter handles closing. */
    static abstract class Base extends Frame {
        final TextField amount = new TextField(10);
        final Choice target = new Choice();
        final Button convertBtn = new Button("Convert");
        final Label result = new Label("                              ");

        Base(String title, int x) {
            super(title);
            setLayout(new FlowLayout());
            target.add("USD"); target.add("EUR");
            add(new Label("INR")); add(amount); add(target); add(convertBtn); add(result);
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { dispose(); }
            });
            setSize(520, 100);
            setLocation(x, 100 + x / 2);
        }

        void doConvert() {
            String msg = convert(amount.getText(), target.getSelectedItem());
            result.setText(msg);
            JOptionPane.showMessageDialog(this, msg, "Conversion", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** (a) The frame itself implements ActionListener. */
    static class StyleA extends Base implements ActionListener {
        StyleA() {
            super("(a) Frame implements ActionListener", 0);
            convertBtn.addActionListener(this);
            amount.addActionListener(this);                 // 5.2: +1 line
        }
        @Override public void actionPerformed(ActionEvent e) { doConvert(); }
    }

    /** (b) Named inner class. */
    static class StyleB extends Base {
        class ConvertHandler implements ActionListener {
            @Override public void actionPerformed(ActionEvent e) { doConvert(); }   // accesses outer fields
        }
        StyleB() {
            super("(b) Named inner class", 60);
            ConvertHandler h = new ConvertHandler();
            convertBtn.addActionListener(h);
            amount.addActionListener(h);                    // 5.2: +1 line, same object reused
        }
    }

    /** (c) Anonymous inner class. */
    static class StyleC extends Base {
        StyleC() {
            super("(c) Anonymous inner class", 120);
            ActionListener h = new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) { doConvert(); }
            };
            convertBtn.addActionListener(h);
            amount.addActionListener(h);                    // 5.2: had to hoist into a variable
        }
    }

    /** (d) Lambda expression. */
    static class StyleD extends Base {
        StyleD() {
            super("(d) Lambda", 180);
            ActionListener h = e -> doConvert();
            convertBtn.addActionListener(h);
            amount.addActionListener(h);                    // 5.2: +1 line
        }
    }

    public static void main(String[] args) {
        String which = args.length > 0 ? args[0] : "all";
        EventQueue.invokeLater(() -> {
            if (which.equals("a") || which.equals("all")) new StyleA().setVisible(true);
            if (which.equals("b") || which.equals("all")) new StyleB().setVisible(true);
            if (which.equals("c") || which.equals("all")) new StyleC().setVisible(true);
            if (which.equals("d") || which.equals("all")) new StyleD().setVisible(true);
        });
    }
}
