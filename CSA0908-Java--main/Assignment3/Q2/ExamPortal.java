import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Q2 - User-defined exception hierarchy and propagation across
 * Controller -> Service -> Storage layers, with chaining.
 */
public class ExamPortal {

    // ---- hierarchy ----
    static class PortalException extends Exception {                       // checked base
        PortalException(String m) { super(m); }
        PortalException(String m, Throwable c) { super(m, c); }
    }
    static class ExamWindowClosedException extends PortalException {      // checked: expected business rule
        ExamWindowClosedException(String m) { super(m); }
    }
    static class NotRegisteredException extends PortalException {         // checked
        NotRegisteredException(String m) { super(m); }
    }
    static class FileTooLargeException extends PortalException {          // checked
        FileTooLargeException(String m) { super(m); }
    }
    static class StorageFailureException extends PortalException {        // checked, wraps IOException
        StorageFailureException(String m, Throwable c) { super(m, c); }
    }
    static class InvalidSubmissionError extends RuntimeException {        // unchecked: programming error
        InvalidSubmissionError(String m) { super(m); }
    }

    // ---- storage layer ----
    static class StorageLayer {
        boolean diskFull;
        void write(String student, long kb) throws StorageFailureException {
            try {
                if (diskFull) throw new IOException("No space left on device");
            } catch (IOException e) {
                throw new StorageFailureException("Could not store answer of " + student, e);
            }
        }
    }

    // ---- service layer ----
    static class ExamService {
        final StorageLayer storage = new StorageLayer();
        final Set<String> registered = Set.of("192311001", "192311002");
        LocalDateTime closesAt = LocalDateTime.now().plusHours(1);

        void submit(String student, long kb) throws PortalException {
            if (student == null) throw new InvalidSubmissionError("student id is null");
            if (LocalDateTime.now().isAfter(closesAt)) throw new ExamWindowClosedException("Exam window closed at " + closesAt);
            if (!registered.contains(student)) throw new NotRegisteredException(student + " not registered for CSA09");
            if (kb > 500) throw new FileTooLargeException("File " + kb + " KB exceeds 500 KB");
            storage.write(student, kb);                                     // propagates (throws)
        }
    }

    // ---- controller layer: final handler ----
    static class Controller {
        final ExamService service = new ExamService();
        String handle(String student, long kb) {
            try {
                service.submit(student, kb);
                return "Submission accepted";
            } catch (ExamWindowClosedException | NotRegisteredException | FileTooLargeException e) {
                return "Rejected: " + e.getMessage();
            } catch (StorageFailureException e) {
                return "Server problem, please retry. (cause logged: " + e.getCause().getMessage() + ")";
            } catch (PortalException e) {
                return "Unexpected portal error: " + e.getMessage();
            }
        }
    }

    public static void main(String[] args) {
        Controller c = new Controller();
        System.out.printf("%-28s | %s%n", "Scenario", "Student sees");
        System.out.printf("%-28s | %s%n", "valid submission", c.handle("192311001", 200));
        System.out.printf("%-28s | %s%n", "not registered", c.handle("192399999", 200));
        System.out.printf("%-28s | %s%n", "file too large", c.handle("192311002", 900));
        c.service.storage.diskFull = true;
        System.out.printf("%-28s | %s%n", "disk full (chained)", c.handle("192311001", 100));
        c.service.storage.diskFull = false;
        c.service.closesAt = LocalDateTime.now().minusMinutes(1);
        System.out.printf("%-28s | %s%n", "window closed", c.handle("192311001", 100));

        System.out.println("\nFull chained stack trace:");
        try {
            StorageLayer s = new StorageLayer(); s.diskFull = true; s.write("192311001", 10);
        } catch (StorageFailureException e) {
            e.printStackTrace(System.out);
            System.out.println("getCause() -> " + e.getCause());
        }

        System.out.println("\nRe-thrown WITHOUT cause (information lost):");
        try {
            try { throw new IOException("No space left on device"); }
            catch (IOException e) { throw new PortalException("Could not store answer"); }
        } catch (PortalException e) {
            e.printStackTrace(System.out);
            System.out.println("getCause() -> " + e.getCause());
        }
    }
}
