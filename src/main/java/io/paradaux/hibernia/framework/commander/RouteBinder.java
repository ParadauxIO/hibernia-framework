package io.paradaux.hibernia.framework.commander;

import io.paradaux.hibernia.framework.commander.annotations.Arg;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.OptionalArg;
import io.paradaux.hibernia.framework.commander.annotations.Permission;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a {@code @Route} pattern, binds the handler method's parameters to it and
 * validates the result at registration time (PAR fail-loud contract): every
 * placeholder must have a matching parameter, every required {@code @Arg} must appear
 * in the route, optional segments must form the tail, and greedy arguments must be
 * terminal. A mismatch throws {@link IllegalStateException} rather than silently
 * dropping the route.
 */
final class RouteBinder {

    RouteBinding bind(Object instance, Method m, Route r, String classPerm) {
        String raw = r.value().trim();
        List<String> parts = raw.isEmpty() ? List.of() : List.of(raw.split("\\s+"));

        List<Segment> segments = new ArrayList<>();
        for (String p : parts) {
            if (p.startsWith("<") && p.endsWith(">")) {
                segments.add(Segment.arg(p.substring(1, p.length() - 1)));
            } else if (p.startsWith("[") && p.endsWith("]")) {
                segments.add(Segment.optionalArg(p.substring(1, p.length() - 1)));
            } else {
                segments.add(Segment.literal(p));
            }
        }

        List<Param> params = new ArrayList<>();
        boolean foundGreedy = false;
        for (Parameter rp : m.getParameters()) {
            boolean isSender = rp.isAnnotationPresent(Sender.class);
            Arg arg = rp.getAnnotation(Arg.class);
            OptionalArg opt = rp.getAnnotation(OptionalArg.class);
            GreedyArg greedy = rp.getAnnotation(GreedyArg.class);

            if (foundGreedy && !isSender) {
                throw new IllegalStateException("@GreedyArg must be the last argument in the route on " + m);
            }

            if (isSender) params.add(Param.sender(rp.getType()));
            else if (greedy != null) {
                foundGreedy = true;
                params.add(Param.greedy(rp.getType(), greedy.value(), greedy.sanitize()));
            }
            else if (arg != null) params.add(Param.required(rp.getType(), arg.value(), arg.sanitize()));
            else if (opt != null) {
                if (rp.getType().isPrimitive() && opt.defaultValue().isEmpty()) {
                    throw new IllegalStateException("@OptionalArg(\"" + opt.value() + "\") on " + m
                            + " has a primitive type but no defaultValue; an omitted argument would be null."
                            + " Provide a defaultValue or use the boxed type.");
                }
                params.add(Param.optional(rp.getType(), opt.value(), opt.defaultValue(), opt.sanitize()));
            }
            else throw new IllegalStateException("Parameter missing @Sender/@Arg/@OptionalArg/@GreedyArg on " + m);
        }

        validateRoute(m, raw, segments, params);

        String methodPerm = Optional.ofNullable(m.getAnnotation(Permission.class)).map(Permission::value).orElse(null);
        String effectivePerm = methodPerm != null ? methodPerm : classPerm;

        String description = Optional.ofNullable(m.getAnnotation(Description.class)).map(Description::value).orElse("");

        return new RouteBinding(instance, m, segments, params, effectivePerm, description, raw);
    }

    /**
     * Registration-time validation: every placeholder must have a matching
     * parameter, every required parameter must appear in the route, optional
     * segments must form the tail, and greedy arguments must be terminal.
     * Failing loud here is the point — a mismatch that slipped through used to
     * silently drop the rest of the route from the command tree.
     */
    private void validateRoute(Method m, String raw, List<Segment> segments, List<Param> params) {
        String where = m.getDeclaringClass().getSimpleName() + "#" + m.getName();
        Set<String> seenNames = new HashSet<>();
        boolean optionalTail = false;

        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            if (seg.literal()) {
                if (optionalTail) {
                    throw new IllegalStateException("Route '" + raw + "' on " + where
                            + ": literal '" + seg.token() + "' cannot follow an optional [segment]");
                }
                continue;
            }
            if (!seenNames.add(seg.token())) {
                throw new IllegalStateException("Route '" + raw + "' on " + where
                        + " uses argument name '" + seg.token() + "' more than once");
            }
            Param param = findParamByName(params, seg.token());
            if (param == null) {
                throw new IllegalStateException("Route '" + raw + "' on " + where
                        + " references argument '" + seg.token() + "' but the method has no"
                        + " @Arg/@OptionalArg/@GreedyArg parameter with that name");
            }
            if (seg.optionalArg()) {
                if (!param.optional) {
                    throw new IllegalStateException("Route '" + raw + "' on " + where
                            + ": [" + seg.token() + "] requires an @OptionalArg parameter (found a required one)");
                }
                optionalTail = true;
            } else if (optionalTail) {
                throw new IllegalStateException("Route '" + raw + "' on " + where
                        + ": required <" + seg.token() + "> cannot follow an optional [segment]");
            }
            if (param.greedy && i != segments.size() - 1) {
                throw new IllegalStateException("Route '" + raw + "' on " + where
                        + ": greedy argument <" + seg.token() + "> must be the last segment");
            }
        }

        for (Param param : params) {
            if (param.sender || param.optional) continue;
            boolean inRoute = segments.stream().anyMatch(s -> !s.literal() && s.token().equals(param.name));
            if (!inRoute) {
                throw new IllegalStateException("@Arg(\"" + param.name + "\") on " + where
                        + " does not appear in route '" + raw + "'; add <" + param.name
                        + "> to the route or make the parameter @OptionalArg");
            }
        }
    }

    static Param findParamByName(List<Param> params, String name) {
        for (Param p : params) {
            if (!p.sender && p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }
}
