package koh.realm.internet.handlers;

import com.google.inject.Inject;
import koh.inter.messages.PlayerComingMessage;
import koh.mina.api.annotations.Disconnect;
import koh.mina.api.annotations.InactiveTimeout;
import koh.mina.api.annotations.Receive;
import koh.patterns.Controller;
import koh.patterns.handler.context.Ctx;
import koh.patterns.handler.context.RequireContexts;
import koh.patterns.services.api.ServiceDependency;
import koh.protocol.client.PregenMessage;
import koh.protocol.client.codec.Dofus2ProtocolEncoder;
import koh.protocol.client.enums.IdentificationFailureReason;
import koh.protocol.client.enums.ServerConnectionError;
import koh.protocol.client.enums.ServerStatusEnum;
import koh.protocol.messages.connection.IdentificationFailedMessage;
import koh.protocol.messages.connection.SelectedServerDataMessage;
import koh.protocol.messages.connection.SelectedServerRefusedMessage;
import koh.protocol.messages.connection.ServerSelectionMessage;
import koh.realm.dao.api.AccountDAO;
import koh.realm.dao.api.GameServerDAO;
import koh.realm.entities.Account;
import koh.realm.entities.GameServer;
import koh.realm.internet.RealmClient;
import koh.realm.internet.RealmContexts;
import koh.realm.utils.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.mina.core.buffer.IoBuffer;

import java.sql.Timestamp;
import java.time.Instant;

@RequireContexts(@Ctx(RealmContexts.Authenticated.class))
public class AuthenticatedHandler implements Controller {

    private static final Logger logger = LogManager.getLogger("RealmServer");

    private final PregenMessage timeOutMessage;

    @Inject
    public AuthenticatedHandler(Dofus2ProtocolEncoder encoder) {
        this.timeOutMessage = new PregenMessage(
                encoder.encodeMessage(new IdentificationFailedMessage(IdentificationFailureReason.TIME_OUT), IoBuffer.allocate(16))
        );
    }

    @Inject private GameServerDAO serverDAO;
    @Inject private AccountDAO accountDAO;

    @InactiveTimeout
    public void onInactivityTimeout(RealmClient client) {
        ThreadContext.put("clientAddress", client.getRemoteAddress().getAddress().getHostAddress());
        try {
            logger.info("Client timed out");
        } finally {
            ThreadContext.remove("clientAddress");
        }
        client.disconnect(timeOutMessage);
    }

    @Receive
    public void onSelect(RealmClient client, ServerSelectionMessage message) throws Exception {
        GameServer server;
        try {
            server = serverDAO.getByKey(message.getServerId());
        }catch(Exception ignored) {
            client.write(new SelectedServerRefusedMessage(
                    message.getServerId(), ServerConnectionError.NO_REASON, ServerStatusEnum.STATUS_UNKNOWN));
            return;
        }
        if (server == null) {
            client.write(new SelectedServerRefusedMessage(
                    message.getServerId(), ServerConnectionError.NO_REASON, ServerStatusEnum.STATUS_UNKNOWN));
            return;
        }
        if (server.getStatus() != ServerStatusEnum.ONLINE) {
            client.write(new SelectedServerRefusedMessage(message.getServerId(), ServerConnectionError.DUE_TO_STATUS,
                    server.getStatus()));
            return;
        }
        if (server.RequiredRole > client.getAccount().get().Right) {
            client.write(new SelectedServerRefusedMessage(message.getServerId(), ServerConnectionError.ACCOUNT_RESTRICTED,
                    server.getStatus()));
            return;
        }

        Account acc = client.getAccount().get();
        String ticket = Util.genTicketID(32).toString();

        server.getClient().write(new PlayerComingMessage(ticket, client.getRemoteAddress().getAddress().toString(),
                acc.ID, acc.NickName, acc.SecretQuestion, acc.SecretAnswer, acc.LastIP, acc.Right, acc.last_login));

        client.getAccount().sync(() -> {
            acc.LastIP = client.getRemoteAddress().getAddress().getHostAddress();
            acc.last_login = Timestamp.from(Instant.now());
            accountDAO.save(acc);
        });
        //TODO : Bool createNewCharacter Size

        client.disconnect(new SelectedServerDataMessage(server.ID, server.Address, server.Port, true, ticket));
    }

    @Disconnect
    public void onDisconnect(RealmClient client) {
        ThreadContext.put("clientAddress", client.getRemoteAddress().getAddress().getHostAddress());
        try {
            logger.info("Client disconnected");
        } finally {
            ThreadContext.remove("clientAddress");
        }
        client.disconnect(false);
    }
}
