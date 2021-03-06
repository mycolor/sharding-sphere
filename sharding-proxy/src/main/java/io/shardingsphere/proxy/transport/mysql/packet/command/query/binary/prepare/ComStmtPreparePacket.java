/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.proxy.transport.mysql.packet.command.query.binary.prepare;

import com.google.common.base.Optional;
import io.shardingsphere.core.constant.DatabaseType;
import io.shardingsphere.core.constant.ShardingConstant;
import io.shardingsphere.core.parsing.SQLParsingEngine;
import io.shardingsphere.core.parsing.parser.sql.SQLStatement;
import io.shardingsphere.core.parsing.parser.sql.dml.insert.InsertStatement;
import io.shardingsphere.core.parsing.parser.sql.dql.select.SelectStatement;
import io.shardingsphere.proxy.config.RuleRegistry;
import io.shardingsphere.proxy.transport.mysql.constant.ColumnType;
import io.shardingsphere.proxy.transport.mysql.packet.MySQLPacketPayload;
import io.shardingsphere.proxy.transport.mysql.packet.command.CommandPacket;
import io.shardingsphere.proxy.transport.mysql.packet.command.CommandResponsePackets;
import io.shardingsphere.proxy.transport.mysql.packet.command.query.ColumnDefinition41Packet;
import io.shardingsphere.proxy.transport.mysql.packet.command.query.binary.PreparedStatementRegistry;
import io.shardingsphere.proxy.transport.mysql.packet.generic.EofPacket;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * COM_STMT_PREPARE command packet.
 * 
 * @see <a href="https://dev.mysql.com/doc/internals/en/com-stmt-prepare.html">COM_STMT_PREPARE</a>
 *
 * @author zhangliang
 */
@Slf4j
public final class ComStmtPreparePacket implements CommandPacket {
    
    private static final RuleRegistry RULE_REGISTRY = RuleRegistry.getInstance();
    
    private static final PreparedStatementRegistry PREPARED_STATEMENT_REGISTRY = PreparedStatementRegistry.getInstance();
    
    @Getter
    private final int sequenceId;
    
    private final String sql;
    
    public ComStmtPreparePacket(final int sequenceId, final MySQLPacketPayload payload) {
        this.sequenceId = sequenceId;
        sql = payload.readStringEOF();
        
    }
    
    @Override
    public void write(final MySQLPacketPayload payload) {
        payload.writeStringEOF(sql);
    }
    
    @Override
    public Optional<CommandResponsePackets> execute() {
        log.debug("COM_STMT_PREPARE received for Sharding-Proxy: {}", sql);
        int currentSequenceId = 0;
        SQLStatement sqlStatement = new SQLParsingEngine(DatabaseType.MySQL, sql, RULE_REGISTRY.getShardingRule(), RULE_REGISTRY.getShardingTableMetaData()).parse(true);
        CommandResponsePackets result = new CommandResponsePackets(
                new ComStmtPrepareOKPacket(++currentSequenceId, PREPARED_STATEMENT_REGISTRY.register(sql), getNumColumns(sqlStatement), sqlStatement.getParametersIndex(), 0));
        for (int i = 0; i < sqlStatement.getParametersIndex(); i++) {
            // TODO add column name
            result.getPackets().add(new ColumnDefinition41Packet(++currentSequenceId, ShardingConstant.LOGIC_SCHEMA_NAME,
                    sqlStatement.getTables().isSingleTable() ? sqlStatement.getTables().getSingleTableName() : "", "", "", "", 100, ColumnType.MYSQL_TYPE_VARCHAR, 0));
        }
        if (sqlStatement.getParametersIndex() > 0) {
            result.getPackets().add(new EofPacket(++currentSequenceId));
        }
        // TODO add If numColumns > 0
        return Optional.of(result);
    }
    
    private int getNumColumns(final SQLStatement sqlStatement) {
        if (sqlStatement instanceof SelectStatement) {
            // TODO select * cannot know items num
            // right now, add metadata, can we know items num?
            return ((SelectStatement) sqlStatement).getItems().size();
        }
        if (sqlStatement instanceof InsertStatement) {
            return ((InsertStatement) sqlStatement).getColumns().size();
        }
        return 0;
    }
}
