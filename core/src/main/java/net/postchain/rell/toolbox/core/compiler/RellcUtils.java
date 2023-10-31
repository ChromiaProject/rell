package net.postchain.rell.toolbox.core.compiler;


import com.github.h0tk3y.betterParse.utils.Tuple2;
import com.github.h0tk3y.betterParse.utils.Tuple3;
import com.github.h0tk3y.betterParse.utils.Tuple4;
import com.github.h0tk3y.betterParse.utils.Tuple5;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.postchain.rell.base.compiler.parser.RellTokenMatch;
import net.postchain.rell.lsp.grammar.AntlrActionEx;
import net.postchain.rell.lsp.grammar.AntlrGrammarGenerator;
import org.antlr.v4.runtime.ParserRuleContext;
import org.apache.commons.text.StringEscapeUtils;

import java.util.*;

final class RellcUtils {
    private RellcUtils() {
    }

    static RellcTransformer transformer(String name) {
        RellcTransformer transformer = Transformers.MAP.get(name);
        Objects.requireNonNull(transformer, String.format("%s", name));
        return transformer;
    }

    static Object token(ParserRuleContext obj) {
        return token0(obj, obj.getText());
    }

    static Object tokenString(ParserRuleContext obj) {
        String text = obj.getText();
        if (!(text.startsWith("'") && text.endsWith("'")) && !(text.startsWith("\"") && text.endsWith("\""))) {
            return null;
        }
        text = text.substring(1, text.length() - 1);
        // Unescaping special characters like: \\n -> \n to match compiler's parser
        String unescaped = StringEscapeUtils.unescapeJava(text);
        return token0(obj, unescaped);
    }

    static Object tokenBytes(ParserRuleContext obj) {
        String text = obj.getText();
        if (!(text.startsWith("x'") && text.endsWith("'")) && !(text.startsWith("x\"") && text.endsWith("\""))) {
            return null;
        }
        text = text.substring(2, text.length() - 1);
        return token0(obj, text);
    }

    private static Object token0(ParserRuleContext obj, String text) {
        Objects.requireNonNull(obj);
        Objects.requireNonNull(text);

        var path = RellcFilePathHolder.INSTANCE.getCurrentFile();

        var pos = new AntlrPos(obj, path.getCPath(), path.getIdePath());

        return new RellTokenMatch(pos, text);
    }

    static Object processList(AntlrToRellContext ctx, Object value) {
        if (value == null) {
            // Can happen in case of a syntax error.
            return Collections.emptyList();
        }

        List<?> eList = (List<?>) value;
        List<Object> res = new ArrayList<>(eList.size());
        for (Object eSub : eList) {
            Object sub = processObject(ctx, eSub);
            if (sub != null) {
                res.add(sub);
            }
        }

        return Collections.unmodifiableList(res);
    }

    static Object processObject(AntlrToRellContext ctx, Object value) {
        ParserRuleContext eObj = (ParserRuleContext) value;
        Object res = AntlrToRell.process(ctx, eObj);
        return res;
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
            Map<String, RellcTransformer> transformers = Maps.transformValues(actions, action -> new RellcTransformer(action));
            return ImmutableMap.copyOf(transformers);
        }
    }
}
