package com.li.lipicturecloud.application.companion.observation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Clock;

/** One owner row keeps its lease across midnight; contains no observation content or credentials. */
@Component
public class ObservationQuota {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public ObservationQuota(JdbcTemplate db, PlatformTransactionManager manager) { this.db=db;tx=new TransactionTemplate(manager); }
    public record Counts(int text,int vision,Instant nextAt) {}
    public Counts status(long owner,Instant now) {
        var rows=db.query("SELECT usageDate,textAttempts,visionAttempts,nextAt FROM companion_observation_usage WHERE subjectId=?",
            (rs,n)->new Counts(rs.getDate(1).toLocalDate().equals(day(now))?rs.getInt(2):0,
                rs.getDate(1).toLocalDate().equals(day(now))?rs.getInt(3):0,instant(rs.getTimestamp(4))),owner);
        return rows.isEmpty()?new Counts(0,0,null):rows.getFirst();
    }
    public boolean claim(long owner,String token,Instant now) {
        // Insert separately: a concurrent duplicate must not poison the locking transaction.
        if (db.queryForObject("SELECT COUNT(*) FROM companion_observation_usage WHERE subjectId=?",Integer.class,owner)==0) {
            try { db.update("INSERT INTO companion_observation_usage(subjectId,usageDate) VALUES (?,?)",owner,day(now)); }
            catch(DuplicateKeyException concurrent) { /* row now exists */ }
        }
        return Boolean.TRUE.equals(tx.execute(status->{
            var rows=db.query("SELECT leaseUntil,nextAt,usageDate FROM companion_observation_usage WHERE subjectId=? FOR UPDATE",
                (rs,n)->new Row(instant(rs.getTimestamp(1)),instant(rs.getTimestamp(2)),rs.getDate(3).toLocalDate()),owner);
            Row row=rows.getFirst();
            if(row.lease()!=null && now.isBefore(row.lease()) || row.next()!=null && now.isBefore(row.next())) return false;
            if(!row.day().equals(day(now))) db.update("UPDATE companion_observation_usage SET usageDate=?,textAttempts=0,visionAttempts=0 WHERE subjectId=?",day(now),owner);
            db.update("UPDATE companion_observation_usage SET leaseToken=?,leaseUntil=?,updateTime=? WHERE subjectId=?",token,Timestamp.from(now.plusSeconds(45)),Timestamp.from(now),owner);
            return true;
        }));
    }
    public boolean reserve(long owner,String token,boolean vision,Instant now) {
        String column=vision?"visionAttempts":"textAttempts";
        int max=vision?ObservationContracts.VISION_DAY_MAX:ObservationContracts.TEXT_DAY_MAX;
        return Boolean.TRUE.equals(tx.execute(status->{
            var days=db.query("SELECT usageDate FROM companion_observation_usage WHERE subjectId=? AND leaseToken=? AND leaseUntil>? FOR UPDATE",
                // Covers the longest 15-second collection plus cancellation/return margin.
                (rs,n)->rs.getDate(1).toLocalDate(),owner,token,Timestamp.from(now.plusSeconds(20)));
            if(days.isEmpty())return false;
            if(!days.getFirst().equals(day(now))) db.update("UPDATE companion_observation_usage SET usageDate=?,textAttempts=0,visionAttempts=0 WHERE subjectId=? AND leaseToken=?",day(now),owner,token);
            return db.update("UPDATE companion_observation_usage SET "+column+"="+column+"+1 WHERE subjectId=? AND leaseToken=? AND "+column+"<?",owner,token,max)==1;
        }));
    }
    public void release(long owner,String token,Instant now,Instant nextAt) {
        db.update("UPDATE companion_observation_usage SET leaseToken=NULL,leaseUntil=NULL,nextAt=?,updateTime=? WHERE subjectId=? AND leaseToken=?",Timestamp.from(nextAt),Timestamp.from(now),owner,token);
    }
    /** Recheck after blocking authorization, without reserving another attempt or reviving an expired lease. */
    public void coverDispatch(long owner,String token,Clock clock) {
        boolean covered=Boolean.TRUE.equals(tx.execute(status->{
            var leases=db.query("SELECT leaseUntil FROM companion_observation_usage WHERE subjectId=? AND leaseToken=? FOR UPDATE",
                (rs,n)->instant(rs.getTimestamp(1)),owner,token);
            Instant now=clock.instant(); // Fresh after acquiring the row lock.
            if(leases.isEmpty() || leases.getFirst()==null || !now.isBefore(leases.getFirst()))return false;
            Instant until=now.plusSeconds(20);
            if(leases.getFirst().isBefore(until))db.update("UPDATE companion_observation_usage SET leaseUntil=?,updateTime=? WHERE subjectId=? AND leaseToken=?",
                Timestamp.from(until),Timestamp.from(now),owner,token);
            return true;
        }));
        if(!covered)throw new IllegalArgumentException("OWNER_BUSY_OR_INTERVAL");
    }
    private record Row(Instant lease,Instant next,LocalDate day) {}
    private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
    private static LocalDate day(Instant now){return LocalDate.ofInstant(now,ZoneOffset.UTC);}
}
