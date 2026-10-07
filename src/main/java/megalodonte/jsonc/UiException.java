package megalodonte.jsonc;

/** Invalid document, reference or configuration. Messages include a document path. */
public final class UiException extends IllegalArgumentException {
    public UiException(String path, String message) { super(path + ": " + message); }
    public UiException(String path, String message, Throwable cause) { super(path + ": " + message, cause); }
}
