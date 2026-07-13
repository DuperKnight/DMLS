package com.duperknight.client.message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
class ServerMessageRouterTest {
 @BeforeEach void reset(){ServerMessageRouter.resetDuplicateStateForTests(); ServerMessageRouter.clearSubscriptionsForTests();}
 @AfterEach void clear(){ServerMessageRouter.clearSubscriptionsForTests();}
 @Test void suppressesOnlyCrossEventDuplicateWithinWindow(){long now=1_000_000_000L; assertFalse(ServerMessageRouter.isCrossEventDuplicate("watched word",MessageOrigin.PLAYER_CHAT,now)); assertTrue(ServerMessageRouter.isCrossEventDuplicate("watched word",MessageOrigin.SERVER_SYSTEM,now+1)); assertFalse(ServerMessageRouter.isCrossEventDuplicate("watched word",MessageOrigin.SERVER_SYSTEM,now+2)); assertFalse(ServerMessageRouter.isCrossEventDuplicate("watched word",MessageOrigin.PLAYER_CHAT,now+300_000_000L));}

 @Test void subscriberFailureDoesNotBlockLaterSubscribers(){
  AtomicInteger delivered=new AtomicInteger();
  ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM),message->{throw new IllegalStateException("fixture");});
  ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM),message->delivered.incrementAndGet());
  ServerMessageRouter.route(new ServerMessage(null,"done",MessageOrigin.SERVER_SYSTEM,false,1L));
  assertEquals(1,delivered.get());
 }

 @Test void subscriptionsCanBeRemoved(){
  AtomicInteger delivered=new AtomicInteger();
  ServerMessageRouter.SubscriptionHandle handle=ServerMessageRouter.subscribe(
          EnumSet.of(MessageOrigin.SERVER_SYSTEM),message->delivered.incrementAndGet());
  handle.unsubscribe();
  ServerMessageRouter.route(new ServerMessage(null,"done",MessageOrigin.SERVER_SYSTEM,false,1L));
  assertEquals(0,delivered.get());
 }
}
