package dev.oswaldo.localai;

import java.time.LocalDate;

public final class PromptPolicy {

    private static final String INSUFFICIENT_CONTEXT = "INSUFFICIENT_CONTEXT";

    private PromptPolicy() {
    }

    public static String systemPrompt() {
        return "You are LocalAiAssistant, a pragmatic local AI assistant. "
                + "Reply in the same language as the user's question. "
                + "If the user writes in Spanish, answer in Spanish. "
                + "If the user writes in English, answer in English. "
                + "Do not invent facts. If you need current or project-specific information that is not available, say so clearly.";
    }

    public static String insufficientContextToken() {
        return INSUFFICIENT_CONTEXT;
    }

    public static String languageInstruction(String contextName) {
        return "First identify the language of the user's question. Do not mention this analysis.\n"
                + "Reply entirely in the same language as the user's question.\n"
                + "The language of any " + contextName + " must not change the reply language.\n"
                + "If the question is in English, answer in English.\n"
                + "If the question is in Spanish, answer in Spanish.\n";
    }

    public static String directAnswerPrompt(String question) {
        return "You are a local AI assistant.\n"
                + "Today's date is " + LocalDate.now() + ". Your training data is older than this date.\n"
                + languageInstruction("supporting context")
                + "If the answer depends on current events, live data, or anything that may have changed recently, do not guess: say you cannot verify it right now.\n"
                + "Never invent facts, names, scores, or dates.\n"
                + "Keep the answer clear and concise.\n\n"
                + "User question: " + question + "\n\n"
                + "Answer:";
    }

    public static String projectAnswerPrompt(String question, String context) {
        return "You are a local AI assistant helping explain a software project.\n"
                + "Answer the user question using only the provided project files.\n"
                + "If the project files do not contain enough information, say that you do not have enough information.\n"
                + "Do not invent files, functions, classes, behavior, or dependencies.\n"
                + "Each project file is provided as a block with a Path and Content. If the question names a file, use the block whose Path matches that file.\n"
                + "When the user asks for exact code details such as signatures, copy them from the provided Content instead of paraphrasing.\n"
                + languageInstruction("project files")
                + "Include the relevant file paths you used.\n\n"
                + "User question: " + question + "\n\n"
                + "Project context:\n"
                + context + "\n\n"
                + "Answer:";
    }

    public static String webAnswerPrompt(String question, String context) {
        return "You are a local AI assistant.\n"
                + "Today's date is " + LocalDate.now() + ". Use it to interpret phrases like this year, this season, or recently.\n"
                + "Answer the user question using only the provided context.\n"
                + "If the context does not contain enough information to answer, reply with exactly this single word and nothing else: " + INSUFFICIENT_CONTEXT + "\n"
                + "Do not invent facts.\n"
                + languageInstruction("web context and sources")
                + "Keep the answer brief and factual.\n\n"
                + "User question: " + question + "\n\n"
                + "Web context:\n"
                + context + "\n\n"
                + "Answer:";
    }

    public static String queryPlannerPrompt(String question) {
        int year = LocalDate.now().getYear();
        return "You are helping a local AI assistant search the web.\n"
                + "Today's date is " + LocalDate.now() + ". Your training data is outdated, so always use this date to pick the correct year in queries about current events, seasons, prices, or versions. Never use a year from memory.\n"
                + "Generate 3 to 5 concise search engine queries that maximize retrieval quality.\n"
                + "Infer the domain from the question.\n"
                + "Prefer keyword-style search queries over full natural-language questions.\n"
                + "Remove conversational filler words and question words.\n"
                + "For non-English questions, translate the search intent to English unless the user explicitly needs sources in that language.\n"
                + "For current-status questions, include terms such as latest, update, news, status, or report when useful.\n"
                + "For people, companies, teams, products, or events, preserve exact named entities and add relevant domain terms when inferable.\n"
                + "For technical questions, include specific domain terminology, likely keywords from documentation/articles, and avoid generic wording.\n"
                + "Prefer web-friendly wording and keep named entities exact.\n"
                + "If the user's question is in Spanish, you may output queries in English when that improves web search quality.\n"
                + "Return only the queries, one per line, with no numbering, no bullets, and no explanation.\n\n"
                + "Examples:\n"
                + "User question: how can I optimize a SQL query\n"
                + "sql query optimization best practices\n"
                + "how to optimize sql queries performance\n"
                + "sql query tuning indexing explain analyze\n"
                + "postgres query optimization guide\n\n"
                + "User question: is Snowflake losing customers?\n"
                + "Snowflake customer churn " + year + "\n"
                + "Snowflake customer count quarterly results " + year + "\n"
                + "Snowflake revenue growth customers " + year + "\n"
                + "analyst report Snowflake demand trends " + year + "\n\n"
                + "User question: " + question + "\n";
    }
}
