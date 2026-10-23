package com.mycelium.opportunities.opportunity;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedService {
  private final JdbcTemplate db;
  private final OpportunityQuery query;

  public SavedService(JdbcTemplate db, OpportunityQuery query) {
    this.db = db;
    this.query = query;
  }

  public Object list(UUID user, int page, String type) {
    if (page < 0 || page > 100000) throw new IllegalArgumentException("Invalid page");
    return db.queryForList(
        "select s.id,s.status,s.opportunity_id,o.title,o.type,o.deadline_at,o.source_url from saved_opportunity s join opportunity o on o.id=s.opportunity_id where s.user_id=? and (?='' or o.type=?) order by o.deadline_at nulls last,s.id limit 20 offset ?",
        user,
        type,
        type,
        page * 20);
  }

  public void save(UUID user, UUID opportunity) {
    query.detail(opportunity);
    db.update(
        "insert into saved_opportunity(id,user_id,opportunity_id) values(?,?,?) on conflict do nothing",
        UUID.randomUUID(),
        user,
        opportunity);
  }

  public void status(UUID user, UUID id, String status) {
    if (!Set.of("SAVED", "REVIEWING", "PLANNING_TO_APPLY", "APPLIED", "WON", "LOST", "DISMISSED")
        .contains(status)) throw new IllegalArgumentException("Invalid workflow state");
    if (db.update(
            "update saved_opportunity set status=? where id=? and user_id=?", status, id, user)
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  public void remove(UUID user, UUID id) {
    if (db.update("delete from saved_opportunity where id=? and user_id=?", id, user) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  public Object dashboard(UUID user) {
    var result =
        db.queryForMap(
            "select (select count(*) from company_match m join company c on c.id=m.company_id join opportunity o on o.id=m.opportunity_id where c.owner_user_id=? and m.relevance_score>0 and o.review_status='APPROVED') matches,(select count(*) from saved_opportunity where user_id=?) saved,(select count(*) from saved_opportunity s join opportunity o on o.id=s.opportunity_id where s.user_id=? and o.deadline_at between now() and now()+interval '7 days') deadlines",
            user,
            user,
            user);
    result.put(
        "newMatches",
        db.queryForObject(
            "select count(distinct o.id) from company_match m join company c on c.id=m.company_id join opportunity o on o.id=m.opportunity_id where c.owner_user_id=? and o.review_status='APPROVED' and m.relevance_score>0 and o.created_at>=now()-interval '7 days'",
            Long.class,
            user));
    for (String type : List.of("TENDER", "GRANT"))
      result.put(
          type,
          db.queryForList(
              "select o.id,o.title,o.deadline_at,max(m.relevance_score) score from company_match m join company c on c.id=m.company_id join opportunity o on o.id=m.opportunity_id where c.owner_user_id=? and o.type=? and o.review_status='APPROVED' and m.relevance_score>0 and "
                  + OpportunityQuery.STATUS
                  + " not in ('CLOSED','CANCELLED','AWARDED') and (o.deadline_at is null or o.deadline_at>now()) group by o.id,o.title,o.deadline_at order by score desc,o.id limit 3",
              user,
              type));
    return result;
  }
}
