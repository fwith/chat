package lexek.wschat.db.dao;

import com.google.common.collect.ImmutableSet;
import lexek.wschat.chat.e.InternalErrorException;
import lexek.wschat.db.model.DataPage;
import lexek.wschat.db.model.JournalEntry;
import lexek.wschat.db.model.UserDto;
import lexek.wschat.util.Pages;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static lexek.wschat.db.jooq.Tables.USER;
import static lexek.wschat.db.jooq.tables.Journal.JOURNAL;

public class JournalDao {
    private static final Set<String> GLOBAL_ACTIONS = ImmutableSet.of("USER_UPDATE", "NEW_EMOTICON", "DELETED_EMOTICON",
        "NAME_CHANGE", "NEW_ROOM", "DELETED_ROOM");
    private final DataSource dataSource;

    public JournalDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void add(JournalEntry journal) {
        UserDto user = journal.getUser();
        UserDto admin = journal.getAdmin();
        try (Connection connection = dataSource.getConnection()) {
            DSL.using(connection)
                .insertInto(JOURNAL)
                .columns(JOURNAL.USER_ID, JOURNAL.ADMIN_ID, JOURNAL.ACTION, JOURNAL.ACTION_DESCRIPTION,
                    JOURNAL.TIME, JOURNAL.ROOM_ID)
                .values(user != null ? user.getId() : null,
                    admin != null ? admin.getId() : null,
                    journal.getAction(),
                    journal.getActionDescription(),
                    new Timestamp(journal.getTime()),
                    journal.getRoomId())
                .execute();
        } catch (DataAccessException | SQLException e) {
            throw new InternalErrorException(e);
        }
    }

    public DataPage<JournalEntry> fetchAllGlobal(int page, int pageSize) {
        try (Connection connection = dataSource.getConnection()) {
            List<JournalEntry> data = DSL.using(connection)
                .selectFrom(JOURNAL
                    .leftOuterJoin(USER).on(JOURNAL.USER_ID.equal(USER.ID))
                    .leftOuterJoin(USER.as("admin")).on(JOURNAL.ADMIN_ID.equal(USER.as("admin").ID)))
                .where(JOURNAL.ACTION.in(GLOBAL_ACTIONS))
                .orderBy(JOURNAL.ID.desc())
                .limit(page * pageSize, pageSize)
                .fetch()
                .stream()
                .map(record -> new JournalEntry(
                    UserDto.fromRecord(record.into(USER)),
                    UserDto.fromRecord(record.into(USER.as("admin"))),
                    record.getValue(JOURNAL.ACTION),
                    record.getValue(JOURNAL.ACTION_DESCRIPTION),
                    record.getValue(JOURNAL.TIME).getTime(),
                    record.getValue(JOURNAL.ROOM_ID)))
                .collect(Collectors.toList());
            int count = DSL.using(connection).fetchCount(JOURNAL, JOURNAL.ACTION.in(GLOBAL_ACTIONS));
            return new DataPage<>(data, page, Pages.pageCount(pageSize, count));
        } catch (DataAccessException | SQLException e) {
            throw new InternalErrorException(e);
        }
    }

    public DataPage<JournalEntry> fetchAllForRoom(int page, int pageSize, long roomId) {
        try (Connection connection = dataSource.getConnection()) {
            List<JournalEntry> data = DSL.using(connection)
                .selectFrom(JOURNAL
                    .leftOuterJoin(USER).on(JOURNAL.USER_ID.equal(USER.ID))
                    .leftOuterJoin(USER.as("admin")).on(JOURNAL.ADMIN_ID.equal(USER.as("admin").ID)))
                .where(JOURNAL.ROOM_ID.equal(roomId))
                .orderBy(JOURNAL.ID.desc())
                .limit(page * pageSize, pageSize)
                .fetch()
                .stream()
                .map(record -> new JournalEntry(
                    UserDto.fromRecord(record.into(USER)),
                    UserDto.fromRecord(record.into(USER.as("admin"))),
                    record.getValue(JOURNAL.ACTION),
                    record.getValue(JOURNAL.ACTION_DESCRIPTION),
                    record.getValue(JOURNAL.TIME).getTime(),
                    record.getValue(JOURNAL.ROOM_ID)))
                .collect(Collectors.toList());
            int count = DSL.using(connection).fetchCount(JOURNAL, JOURNAL.ROOM_ID.equal(roomId));
            return new DataPage<>(data, page, Pages.pageCount(pageSize, count));
        } catch (DataAccessException | SQLException e) {
            throw new InternalErrorException(e);
        }
    }
}
