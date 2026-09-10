package com.worklog.chat;

import com.worklog.config.KstDates;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "조웅식의 오늘 업무일지를 요약해서 보내줘" 같은 문장을 읽는다.
 *
 * <p>문법을 만들지 않는다. 채널에는 온갖 말이 오가므로, (1) 업무 일지를 묻는 말인지(얼마나 분명한지),
 * (2) 어느 날인지, (3) 명단에 있는 이름이 들어 있는지만 본다. 나머지 말은 무시한다.
 * 이름은 명단(사원·계정 이름)과 대조해서 찾는다 — 한국어에서 이름과 조사("조웅식의",
 * "조웅식이")를 문법으로 떼어 내는 것보다, 아는 이름이 글자로 들어 있는지 보는 편이 확실하다.
 */
public final class WorkLogQueryParser {

    private WorkLogQueryParser() {}

    /** 어느 정도 확실하게 업무 일지를 묻는 말인지. */
    public enum Intent {
        /** 업무 일지와 무관한 말 */
        NONE,
        /** "요약"·"뭐함" 처럼 다른 뜻으로도 쓰이는 말 — 이름이 함께 있을 때만 답한다 */
        WEAK,
        /** "업무일지" 처럼 뜻이 분명한 말 — 이름이 없어도 쓰는 법을 알려 준다 */
        STRONG
    }

    private static final String[] STRONG_WORDS = {"업무일지", "업무 일지", "일지", "작업 내역", "작업내역", "작업내용", "작업 내용"};
    private static final String[] WEAK_WORDS = {
        "요약", "정리", "업무", "뭐 했", "뭐했", "뭐 함", "뭐함", "뭐 해", "뭐해", "한 일", "한일", "한 거", "한거", "활동", "커밋", "작업"
    };

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern KOREAN_DATE = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern SLASH_DATE = Pattern.compile("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d)");

    /**
     * 업무 일지를 묻는 말인지, 얼마나 분명한지.
     *
     * <p>"회의 요약 올립니다" 같은 말에 봇이 "누구의 일지인지 못 찾았습니다" 라고 끼어들면 곤란하다.
     * 그래서 느슨한 말(요약·정리·뭐함)은 {@link Intent#WEAK} 로 두고, 답할지는 이름이 있는지로 정한다.
     */
    public static Intent intentOf(String text) {
        if (text == null) {
            return Intent.NONE;
        }
        for (String w : STRONG_WORDS) {
            if (text.contains(w)) {
                return Intent.STRONG;
            }
        }
        for (String w : WEAK_WORDS) {
            if (text.contains(w)) {
                return Intent.WEAK;
            }
        }
        return Intent.NONE;
    }

    /** 업무 일지를 묻는 말인지 (분명하든 느슨하든). */
    public static boolean asksForWorkLog(String text) {
        return intentOf(text) != Intent.NONE;
    }

    /**
     * 날짜 표현을 읽는다. 없으면 오늘(KST).
     *
     * <p>절대 날짜(2026-09-10, 9월 10일, 9/10)가 상대 표현(어제)보다 우선한다 — 둘 다 있으면
     * 구체적인 쪽이 뜻하는 바가 분명하다.
     */
    public static LocalDate dateIn(String text, LocalDate today) {
        if (text == null) {
            return today;
        }
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            return safeDate(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)), today);
        }
        Matcher kr = KOREAN_DATE.matcher(text);
        if (kr.find()) {
            return safeDate(today.getYear(), Integer.parseInt(kr.group(1)), Integer.parseInt(kr.group(2)), today);
        }
        Matcher slash = SLASH_DATE.matcher(text);
        if (slash.find()) {
            return safeDate(today.getYear(), Integer.parseInt(slash.group(1)), Integer.parseInt(slash.group(2)), today);
        }
        if (text.contains("그저께") || text.contains("그제")) {
            return today.minusDays(2);
        }
        if (text.contains("어제")) {
            return today.minusDays(1);
        }
        return today;
    }

    public static LocalDate dateIn(String text) {
        return dateIn(text, KstDates.today());
    }

    /**
     * 문장 안에 들어 있는 이름을 명단에서 찾는다. 여러 개가 걸리면 가장 긴 이름을 고른다 —
     * "김민"과 "김민수"가 둘 다 있을 때 "김민수의 일지"는 김민수를 뜻한다.
     */
    public static Optional<String> personIn(String text, Collection<String> knownNames) {
        if (text == null || knownNames == null) {
            return Optional.empty();
        }
        return knownNames.stream()
                .filter(n -> n != null && n.length() >= 2 && text.contains(n))
                .max(Comparator.comparingInt(String::length));
    }

    /** 잘못된 날짜(2월 30일)는 없는 것으로 보고 오늘로 둔다. */
    private static LocalDate safeDate(int year, int month, int day, LocalDate fallback) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return fallback;
        }
    }
}
