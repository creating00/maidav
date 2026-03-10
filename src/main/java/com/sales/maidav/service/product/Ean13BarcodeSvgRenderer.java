package com.sales.maidav.service.product;

final class Ean13BarcodeSvgRenderer {

    private static final String[] L_CODES = {
            "0001101", "0011001", "0010011", "0111101", "0100011",
            "0110001", "0101111", "0111011", "0110111", "0001011"
    };
    private static final String[] G_CODES = {
            "0100111", "0110011", "0011011", "0100001", "0011101",
            "0111001", "0000101", "0010001", "0001001", "0010111"
    };
    private static final String[] R_CODES = {
            "1110010", "1100110", "1101100", "1000010", "1011100",
            "1001110", "1010000", "1000100", "1001000", "1110100"
    };
    private static final String[] PARITY_PATTERNS = {
            "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
            "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
    };

    String render(String barcode) {
        String normalized = normalize(barcode);
        if (normalized.length() != 13 || !normalized.matches("^\\d{13}$")) {
            throw new InvalidProductException("La etiqueta imprimible requiere un codigo EAN-13 de 13 digitos");
        }
        if (!hasValidCheckDigit(normalized)) {
            throw new InvalidProductException("El codigo EAN-13 no es valido");
        }

        String pattern = buildPattern(normalized);
        int moduleWidth = 3;
        int quietZone = 10 * moduleWidth;
        int barHeight = 110;
        int guardHeight = 122;
        int totalWidth = quietZone * 2 + pattern.length() * moduleWidth;
        int totalHeight = 170;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(totalWidth)
                .append("\" height=\"").append(totalHeight)
                .append("\" viewBox=\"0 0 ").append(totalWidth).append(' ').append(totalHeight).append("\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>");

        int x = quietZone;
        for (int i = 0; i < pattern.length(); i++) {
            if (pattern.charAt(i) == '1') {
                boolean guardBar = i < 3 || (i >= 45 && i < 50) || i >= 92;
                int height = guardBar ? guardHeight : barHeight;
                svg.append("<rect x=\"").append(x)
                        .append("\" y=\"10\" width=\"").append(moduleWidth)
                        .append("\" height=\"").append(height)
                        .append("\" fill=\"#111827\"/>");
            }
            x += moduleWidth;
        }

        appendText(svg, 8, 148, normalized.substring(0, 1), 20);
        appendText(svg, quietZone + 16, 148, normalized.substring(1, 7), 20);
        appendText(svg, quietZone + (50 * moduleWidth) + 10, 148, normalized.substring(7), 20);
        svg.append("</svg>");
        return svg.toString();
    }

    private String buildPattern(String barcode) {
        StringBuilder pattern = new StringBuilder("101");
        String parity = PARITY_PATTERNS[Character.digit(barcode.charAt(0), 10)];

        for (int i = 1; i <= 6; i++) {
            int digit = Character.digit(barcode.charAt(i), 10);
            pattern.append(parity.charAt(i - 1) == 'L' ? L_CODES[digit] : G_CODES[digit]);
        }

        pattern.append("01010");

        for (int i = 7; i <= 12; i++) {
            int digit = Character.digit(barcode.charAt(i), 10);
            pattern.append(R_CODES[digit]);
        }

        pattern.append("101");
        return pattern.toString();
    }

    private void appendText(StringBuilder svg, int x, int y, String value, int fontSize) {
        svg.append("<text x=\"").append(x)
                .append("\" y=\"").append(y)
                .append("\" font-family=\"monospace\" font-size=\"").append(fontSize)
                .append("\" fill=\"#111827\">")
                .append(value)
                .append("</text>");
    }

    private String normalize(String barcode) {
        return barcode == null ? "" : barcode.trim();
    }

    private boolean hasValidCheckDigit(String barcode) {
        int expected = ean13CheckDigit(barcode.substring(0, 12));
        return Character.digit(barcode.charAt(12), 10) == expected;
    }

    private int ean13CheckDigit(String firstTwelveDigits) {
        int sum = 0;
        for (int i = 0; i < firstTwelveDigits.length(); i++) {
            int digit = Character.digit(firstTwelveDigits.charAt(i), 10);
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }
}
