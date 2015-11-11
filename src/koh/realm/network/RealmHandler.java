package koh.realm.network;

import com.google.inject.Inject;
import koh.patterns.event.EventExecutor;
import koh.patterns.handler.ConsumerHandlerExecutor;
import koh.protocol.client.Message;
import koh.protocol.messages.handshake.ProtocolRequired;
import koh.protocol.messages.security.RawDataMessage;
import koh.realm.app.Logs;
import koh.patterns.handler.ConsumerHandlingProvider;
import koh.realm.entities.GameServer;
import koh.realm.inter.events.GameServerAuthenticatedEvent;
import koh.realm.network.annotations.RealmPackage;
import koh.realm.utils.Settings;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

/**
 *
 * @author Neo-Craft
 */
public class RealmHandler extends IoHandlerAdapter {

    private final byte[] rawBytes = null;
    private final char[] binaryKeys = null ;

    private final Settings settings;
    private final Logs logs;

    private final ConsumerHandlerExecutor<RealmClient, Message> handlers;

    @Inject
    public RealmHandler(Settings settings, Logs logs, @RealmPackage ConsumerHandlerExecutor<RealmClient, Message> handlers, @RealmPackage EventExecutor events) {
        this.logs = logs;
        this.settings = settings;
        this.handlers = handlers;
        events.fire(new GameServerAuthenticatedEvent(new GameServer(), 33));
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        session.setAttribute("session", new RealmClient(session));
        session.write(new ProtocolRequired(settings.getIntElement("Protocol.requiredVersion"),
                settings.getIntElement("Protocol.currentVersion")));
        session.write(new RawDataMessage((short) rawBytes.length, rawBytes));
    }

    @Override
    public void messageReceived(IoSession session, Object arg1) throws Exception {
        Message message = (Message) arg1;
        logs.writeDebug("[DEBUG] Client recv >> " + message.getClass().getSimpleName());

        Object objClient = session.getAttribute("session");
        if (objClient != null && objClient instanceof RealmClient) {
            RealmClient client = (RealmClient) objClient;
            try {
                handlers.handle(client, message);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            //client.parsePacket(message);
        }
    }

    @Override
    public void messageSent(IoSession session, Object arg1) throws Exception {
        Message message = (Message) arg1;
        logs.writeDebug("[DEBUG] Client send >> " + message.getClass().getSimpleName());
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        Object objClient = session.getAttribute("session");
        if (objClient != null && objClient instanceof RealmClient) {
            RealmClient client = (RealmClient) objClient;
            client.timeOut();
        }
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        Object objClient = session.getAttribute("session");
        if (objClient != null && objClient instanceof RealmClient) {
            RealmClient client = (RealmClient) objClient;
            client.close();
        }
        session.removeAttribute("session");

    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        Object objClient = session.getAttribute("session");
        if (objClient != null && objClient instanceof RealmClient) {
            RealmClient client = (RealmClient) objClient;
            client.close();
        }
    }

}
