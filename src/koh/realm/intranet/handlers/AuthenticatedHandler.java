package koh.realm.intranet.handlers;

import com.google.inject.Inject;
import koh.inter.messages.*;
import koh.mina.api.annotations.Disconnect;
import koh.mina.api.annotations.Receive;
import koh.patterns.Controller;
import koh.patterns.event.api.Listen;
import koh.patterns.handler.context.Ctx;
import koh.patterns.handler.context.RequireContexts;
import koh.protocol.messages.connection.ServerStatusUpdateMessage;
import koh.realm.Main;
import koh.realm.dao.api.AccountDAO;
import koh.realm.dao.api.BannedAddressDAO;
import koh.realm.dao.api.CharacterDAO;
import koh.realm.entities.Account;
import koh.realm.internet.RealmClient;
import koh.realm.intranet.GameServerClient;
import koh.realm.intranet.InterServerContexts;
import koh.realm.intranet.events.ServerStatusChangedEvent;
import koh.realm.internet.RealmContexts;
import koh.realm.internet.RealmServer;
import koh.repositories.RepositoryReference;
import lombok.extern.log4j.Log4j2;

@RequireContexts(@Ctx(value = InterServerContexts.Authenticated.class))
@Log4j2
public class AuthenticatedHandler implements Controller {

    @Inject
    private RealmServer realmServer;

    @Inject
    private CharacterDAO characterDAO;

    @Inject
    private AccountDAO accountDAO;

    @Inject
    private BannedAddressDAO addressDAO;

    @Receive
    public void onPlayerCreated(GameServerClient server, PlayerCreatedMessage message) throws Exception {
        characterDAO.insertOrUpdate(message.accountId, server.getEntity().ID, (short)message.currentCount);
        final RepositoryReference<Account> target = accountDAO.getAccount(message.accountId);
        if(target != null && target.get() != null) {
            target.get().characters.put(server.getEntity().ID, (byte) message.currentCount);
        }
    }

    @Receive
    public void onPong(GameServerClient server, PingMessage message){
        server.write(message);
    }

    @Receive
    public void onAddressUnSuspended(GameServerClient server , AddressTookAwayMessage message){
        RepositoryReference<Account> target = accountDAO.getAccount(message.accountId);
        if(target == null) {
            log.error("Fail to found the unsuspended account's ip {}", message.accountId);
        }
        else{
            target.get().suspendedTime = 0;
            accountDAO.updateBlame(target.get());
            this.addressDAO.remove(target.get().lastIP);
        }
    }

    @Receive
    public void onPlayerUnSuspended(GameServerClient server , AccountTookAwayMessage message) throws Exception{
        RepositoryReference<Account> target = accountDAO.getAccount(message.accountId);
        if(target == null) {
            log.error("Fail to found the unsuspended account {}", message.accountId);
        }
        else{
            target.get().suspendedTime = 0;
            accountDAO.updateBlame(target.get());
        }
    }

    @Receive
    public void onPlayerAddressSuspended(GameServerClient server, PlayerAddressSuspendedMessage message) throws Exception {
        this.onPlayerSuspended(server, message);
        this.addressDAO.add(message.address,message.time);
    }

    @Receive
    public void onPlayerSuspended(GameServerClient server, PlayerSuspendedMessage message) throws Exception {
        final RepositoryReference<Account> target = accountDAO.getAccount(message.accountId);
        if(target == null) {
            log.error("Fail to found the suspended account {}", message.accountId);
        }
        else{
            target.get().suspendedTime = message.time;
            accountDAO.updateBlame(target.get());
        }
    }

    @Listen
    public void onStatusChanged(ServerStatusChangedEvent event) {
        Main.REALM.getMina().broadcast(new ServerStatusUpdateMessage(event.entity.toInformations()),
                (client) -> client.getHandlerContext() == RealmContexts.AUTHENTICATED);
    }

    @Disconnect
    public void onDisconnect(GameServerClient server) throws Exception {
        server.disconnect(false);
    }

}