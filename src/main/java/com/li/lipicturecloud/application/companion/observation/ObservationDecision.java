package com.li.lipicturecloud.application.companion.observation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.li.lipicturecloud.application.companion.presence.PresenceDecision;
import java.util.Set;

public record ObservationDecision(String action,String emote,String text,int nextObservationSeconds,String focus,String topic) {
    private static final Set<String> FIELDS=Set.of("action","emote","text","nextObservationSeconds","focus","topic");
    public static ObservationDecision parse(String raw) {
        try {
            if(raw==null || raw.isBlank() || raw.length()>1024) throw new IllegalArgumentException();
            var mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            var node=mapper.readTree(raw);
            if(!node.isObject()) throw new IllegalArgumentException();
            node.fieldNames().forEachRemaining(key->{if(!FIELDS.contains(key))throw new IllegalArgumentException();});
            var base=mapper.createObjectNode();
            for(String key:ListHolder.BASE) {if(!node.has(key))throw new IllegalArgumentException();base.set(key,node.get(key));}
            var decision=PresenceDecision.parse(mapper.writeValueAsString(base));
            int next=90;
            if(node.has("nextObservationSeconds") && !node.get("nextObservationSeconds").isNull()) {
                if(!node.get("nextObservationSeconds").canConvertToInt() || !node.get("nextObservationSeconds").isIntegralNumber()) throw new IllegalArgumentException();
                next=Math.max(90,Math.min(600,node.get("nextObservationSeconds").intValue()));
            }
            return new ObservationDecision(decision.action(),decision.emote(),decision.text(),next,shortValue(node,"focus"),shortValue(node,"topic"));
        }catch(Exception invalid){throw new IllegalArgumentException("INVALID_OUTPUT");}
    }
    private static String shortValue(JsonNode node,String field) {
        var value=node.get(field);if(value==null || value.isNull())return null;
        if(!value.isTextual() || value.textValue().length()>100 || value.textValue().codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException();
        return value.textValue();
    }
    private static class ListHolder { static final String[] BASE={"action","emote","text"}; }
}
