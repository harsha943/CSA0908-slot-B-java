import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 3 - Campus library search screen (AWT).
 * Frame: BorderLayout
 *   NORTH  : top Panel (GridLayout 2x1) -> title Label + search row Panel (FlowLayout)
 *   CENTER : results TextArea  (grows on resize)
 *   SOUTH  : bottom Panel (BorderLayout) -> buttons Panel (FlowLayout) + status Label
 */
public class LibrarySearch extends Frame {
    private final TextField query = new TextField(25);
    private final Choice category = new Choice();
    private final TextArea results = new TextArea();
    private final Label status = new Label("Ready");
    private final List<String> catalogue = List.of(
            "Java: The Complete Reference | Computer Science", "Effective Java | Computer Science",
            "Clean Code | Computer Science", "Engineering Mathematics | Mathematics",
            "Linear Algebra Done Right | Mathematics", "Thermodynamics | Mechanical",
            "Head First Java | Computer Science", "Data Structures in Java | Computer Science");
    private List<String> hits = new ArrayList<>();
    private int page = 0;
    private static final int PAGE_SIZE = 3;

    public LibrarySearch() {
        super("Campus Library");
        setLayout(new BorderLayout(4, 4));

        Label title = new Label("Campus Library - Book Search", Label.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        Panel searchRow = new Panel(new FlowLayout(FlowLayout.LEFT));
        for (String c : new String[]{"All", "Computer Science", "Mathematics", "Mechanical"}) category.add(c);
        Button search = new Button("Search");
        searchRow.add(new Label("Title:")); searchRow.add(query); searchRow.add(category); searchRow.add(search);
        Panel top = new Panel(new GridLayout(2, 1));
        top.add(title); top.add(searchRow);

        results.setEditable(false);

        Panel buttons = new Panel(new FlowLayout(FlowLayout.CENTER));
        Button prev = new Button("Prev"), next = new Button("Next"), reserve = new Button("Reserve");
        buttons.add(prev); buttons.add(next); buttons.add(reserve);
        Panel bottom = new Panel(new BorderLayout());
        bottom.add(buttons, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(results, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        ActionListener doSearch = e -> search();
        search.addActionListener(doSearch);
        query.addActionListener(doSearch);
        prev.addActionListener(e -> { if (page > 0) { page--; showPage(); } });
        next.addActionListener(e -> { if ((page + 1) * PAGE_SIZE < hits.size()) { page++; showPage(); } });
        reserve.addActionListener(e -> status.setText(hits.isEmpty() ? "Search first"
                : "Reserved: " + hits.get(page * PAGE_SIZE).split("\\|")[0].trim()));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); }
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                status.setText("Window " + getWidth() + " x " + getHeight()
                        + " | results area " + results.getWidth() + " x " + results.getHeight());
            }
        });
        setSize(600, 400);
    }

    private void search() {
        String q = query.getText().trim().toLowerCase();
        String cat = category.getSelectedItem();
        hits = new ArrayList<>();
        for (String b : catalogue)
            if (b.toLowerCase().contains(q) && (cat.equals("All") || b.endsWith(cat))) hits.add(b);
        page = 0;
        showPage();
    }

    private void showPage() {
        StringBuilder sb = new StringBuilder();
        int from = page * PAGE_SIZE, to = Math.min(hits.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) sb.append(i + 1).append(". ").append(hits.get(i)).append('\n');
        results.setText(hits.isEmpty() ? "No books found" : sb.toString());
        status.setText(hits.size() + " result(s), page " + (page + 1));
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> new LibrarySearch().setVisible(true));
    }
}
