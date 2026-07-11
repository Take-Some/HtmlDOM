package dev.takesome.htmldom.css;

/** Resolved, renderer-neutral mask configuration for a DOM element. */
public record UiCssMask(
        UiMaskImage image,
        UiMaskMode mode,
        UiMaskRepeat repeat,
        String size,
        String position,
        double opacity,
        boolean invert,
        int sampleSize
) {
    public UiCssMask {
        image = image == null ? UiMaskImage.none() : image;
        mode = mode == null ? UiMaskMode.MATCH_SOURCE : mode;
        repeat = repeat == null ? UiMaskRepeat.REPEAT : repeat;
        size = size == null ? "auto" : size.trim();
        position = position == null ? "0% 0%" : position.trim();
        opacity = Math.max(0.0D, Math.min(1.0D, opacity));
        sampleSize = Math.max(1, Math.min(8, sampleSize));
    }

    public static UiCssMask none() {
        return new UiCssMask(UiMaskImage.none(), UiMaskMode.MATCH_SOURCE, UiMaskRepeat.REPEAT,
                "auto", "0% 0%", 1.0D, false, 1);
    }

    public boolean active() {
        return image.active() && opacity > 0.0D;
    }
}
