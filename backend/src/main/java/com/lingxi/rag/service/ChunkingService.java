package com.lingxi.rag.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分块服务：按段落贪心聚合 + 尾部重叠 + 超长块句子切分。纯函数、可单测。
 */
@Service
public class ChunkingService {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？!?；;.\\n])");
    private static final Pattern EXCESS_BLANK = Pattern.compile("\\n{3,}");

    public List<String> chunk(String text) {
        return chunk(text, 600, 120);
    }

    public List<String> chunk(String text, int targetChars, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String cleaned = EXCESS_BLANK.matcher(text.replace("\r\n", "\n")).replaceAll("\n\n").strip();
        if (cleaned.length() <= targetChars) {
            chunks.add(cleaned);
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (String block : cleaned.split("\n")) {
            String line = block.strip();
            if (line.isEmpty()) {
                continue;
            }
            for (String piece : splitOversize(line, targetChars)) {
                if (current.length() + piece.length() + 1 > targetChars && current.length() > 0) {
                    chunks.add(current.toString().strip());
                    String tail = current.length() > overlap
                            ? current.substring(current.length() - overlap) : current.toString();
                    current = new StringBuilder(tail.strip());
                }
                current.append(piece).append('\n');
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    /** 单段超长时按句子边界二次切分。 */
    private List<String> splitOversize(String line, int targetChars) {
        if (line.length() <= targetChars) {
            return List.of(line);
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String sentence : SENTENCE_SPLIT.split(line)) {
            String s = sentence.strip();
            if (s.isEmpty()) {
                continue;
            }
            if (s.length() > targetChars) {
                // 仍超长则硬切
                for (int i = 0; i < s.length(); i += targetChars) {
                    pieces.add(s.substring(i, Math.min(s.length(), i + targetChars)));
                }
                continue;
            }
            if (piece.length() + s.length() + 1 > targetChars && piece.length() > 0) {
                pieces.add(piece.toString().strip());
                piece.setLength(0);
            }
            piece.append(s).append(' ');
        }
        if (!piece.isEmpty()) {
            pieces.add(piece.toString().strip());
        }
        return pieces;
    }
}
