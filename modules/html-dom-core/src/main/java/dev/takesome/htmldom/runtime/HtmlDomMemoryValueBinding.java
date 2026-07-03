package dev.takesome.htmldom.runtime;

/** In-memory runtime value binding for document-local UI state. */
public final class HtmlDomMemoryValueBinding implements HtmlDomValueBinding {
    private Object value;

    public HtmlDomMemoryValueBinding(Object value) {
        this.value = value;
    }

    @Override
    public Object get() {
        return value;
    }

    @Override
    public void set(Object value) {
        this.value = value;
    }
}
