package com.li.lipicturecloud.application.companion.observation;

/** Both route credentials, including mixed and repeatedly JSON-escaped echoes. Request local only. */
public final class ObservationRedaction {
    private final String[] keys;
    public ObservationRedaction(String... keys){this.keys=keys.clone();}
    public String safe(String value) {
        if(value==null)return null;
        String decoded=value;boolean hit=false;
        for(int depth=0;depth<=Math.min(value.length(),1024);depth++) {
            for(String key:keys) if(key!=null && !key.isEmpty() && decoded.contains(key)) { decoded=decoded.replace(key,"[REDACTED_CREDENTIAL]");hit=true; }
            String next=decode(decoded);if(next.equals(decoded))break;decoded=next;
        }
        return hit?decoded:value;
    }
    private static String decode(String value) {
        var out=new StringBuilder();
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\\' && i+1<value.length()) {
                char n=value.charAt(i+1);
                if(n=='u' && i+5<value.length()) {try {out.append((char)Integer.parseInt(value.substring(i+2,i+6),16));i+=5;continue;}catch(NumberFormatException ignored){}}
                Character d=switch(n){case '"','\\','/'->n;case 'b'->'\b';case 'f'->'\f';case 'n'->'\n';case 'r'->'\r';case 't'->'\t';default->null;};
                if(d!=null){out.append(d);i++;continue;}
            }
            out.append(c);
        }
        return out.toString();
    }
    @Override public String toString(){return "ObservationRedaction[credentials omitted]";}
}
