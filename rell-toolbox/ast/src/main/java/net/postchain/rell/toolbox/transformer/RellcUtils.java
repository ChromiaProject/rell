package net.postchain.rell.toolbox.transformer;


import com.github.h0tk3y.betterParse.utils.Tuple2;
import com.github.h0tk3y.betterParse.utils.Tuple3;
import com.github.h0tk3y.betterParse.utils.Tuple4;
import com.github.h0tk3y.betterParse.utils.Tuple5;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.postchain.rell.base.compiler.ast.S_BasicPos;
import net.postchain.rell.base.compiler.ast.S_Comment;
import net.postchain.rell.base.compiler.parser.RellTokenMatch;
import net.postchain.rell.lsp.grammar.AntlrActionEx;
import net.postchain.rell.lsp.grammar.AntlrGrammarGenerator;
import net.postchain.rell.toolbox.compiler.RellCompilerFilePathHolder;
import net.postchain.rell.toolbox.parser.RellCommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.apache.commons.text.StringEscapeUtils;

import java.util.*;

final class RellcUtils {

    private static final String SINGLE_QUOTE = "'";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String BYTE_ARRAY_START_SINGLE_QUOTE = "x" + SINGLE_QUOTE;
    private static final String BYTE_ARRAY_START_DOUBLE_QUOTE = "x" + DOUBLE_QUOTE;

    private RellcUtils() {
    }

    static RellcTransformer transformer(String name) {
        RellcTransformer transformer = Transformers.MAP.get(name);
        Objects.requireNonNull(transformer, String.format("Transformer not found for: %s", name));
        return transformer;
    }

    static Object token(AntlrToRellContext ctx, ParserRuleContext obj) {
        return token0(obj, obj.getText(), ctx.getTokenStream());
    }

    private static S_Comment getRellDocComment(ParserRuleContext obj, TokenStream tokenStream) {
        RellCommonTokenStream rellCommonTokenStream = ((RellCommonTokenStream) tokenStream);
        Token token = rellCommonTokenStream.getPreviousRellDocCommentToken(obj.start.getTokenIndex());
        if (token == null) {
            return null;
        }

        var path = RellCompilerFilePathHolder.INSTANCE.getCurrentFile();
        var pos = new S_BasicPos(path.getCPath(), path.getIdePath(), token.getStartIndex(), token.getLine(), token.getCharPositionInLine() + 1);
        return new S_Comment(pos, token.getText());
    }

    static Object tokenString(AntlrToRellContext ctx, ParserRuleContext obj) {
        String text = obj.getText();
        if (!(text.startsWith(SINGLE_QUOTE) && text.endsWith(SINGLE_QUOTE)) && !(text.startsWith(DOUBLE_QUOTE) && text.endsWith(DOUBLE_QUOTE))) {
            return null;
        }
        text = extractStringContent(text);
        // Unescaping special characters like: \\n -> \n to match compiler's parser
        String unescaped = StringEscapeUtils.unescapeJava(text);
        return token0(obj, unescaped, ctx.getTokenStream());
    }

    private static String extractStringContent(String text) {
        return text.substring(1, text.length() - 1);
    }

    static Object tokenBytes(AntlrToRellContext ctx, ParserRuleContext obj) {
        String text = obj.getText();
        if (!(text.startsWith(BYTE_ARRAY_START_SINGLE_QUOTE) && text.endsWith(SINGLE_QUOTE)) &&
                !(text.startsWith(BYTE_ARRAY_START_DOUBLE_QUOTE) && text.endsWith(DOUBLE_QUOTE))) {
            return null;
        }
        text = extractByteArrayContent(text);
        return token0(obj, text, ctx.getTokenStream());
    }

    private static String extractByteArrayContent(String text) {
        return text.substring(2, text.length() - 1);
    }

    private static Object token0(ParserRuleContext obj, String text, TokenStream tokenStream) {
        Objects.requireNonNull(obj);
        Objects.requireNonNull(text);

        var path = RellCompilerFilePathHolder.INSTANCE.getCurrentFile();

        var pos = new AntlrPos(obj, path.getCPath(), path.getIdePath());

        var comment = getRellDocComment(obj, tokenStream);

        return new RellTokenMatch(pos, text, comment);
    }

    static Object processList(AntlrToRellContext ctx, List<? extends ParserRuleContext> values) {
        if (values == null) {
            // Can happen in case of a syntax error.
            return Collections.emptyList();
        }

        List<Object> res = new ArrayList<>(values.size());
        for (ParserRuleContext eSub : values) {
            Object sub = processObject(ctx, eSub);
            if (sub != null) {
                res.add(sub);
            }
        }

        return Collections.unmodifiableList(res);
    }

    static Object processObject(AntlrToRellContext ctx, ParserRuleContext value) {
        return AntlrToRell.process(ctx, value);
    }

    static Object tuple(Object v1) {
        return v1;
    }

    static Object tuple(Object v1, Object v2) {
        return new Tuple2<>(v1, v2);
    }

    static Object tuple(Object v1, Object v2, Object v3) {
        return new Tuple3<>(v1, v2, v3);
    }

    static Object tuple(Object v1, Object v2, Object v3, Object v4) {
        return new Tuple4<>(v1, v2, v3, v4);
    }

    static Object tuple(Object v1, Object v2, Object v3, Object v4, Object v5) {
        return new Tuple5<>(v1, v2, v3, v4, v5);
    }

    private static final class Transformers {
        static final Map<String, RellcTransformer> MAP = createTransformers();

        private static Map<String, RellcTransformer> createTransformers() {
            Map<String, AntlrActionEx> actions = AntlrGrammarGenerator.generateAntlrActions();
            actions = Maps.filterValues(actions, action -> action.getTransform() != null);
            Map<String, RellcTransformer> transformers = Maps.transformValues(actions, RellcTransformer::new);
            return ImmutableMap.copyOf(transformers);
        }
    }
}
