/*
 * Copyright 2009 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.tomp2p.rpc;
import net.tomp2p.connection.ConnectionBean;
import net.tomp2p.connection.PeerBean;
import net.tomp2p.connection.PeerException;
import net.tomp2p.futures.FutureResponse;
import net.tomp2p.message.Message;
import net.tomp2p.message.MessageID;
import net.tomp2p.peers.PeerMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Is able to send messages (as a request) and processes incoming replies.
 * 
 * @author Thomas Bocek
 * 
 */
public class RequestHandlerTCP
{
	final private static Logger logger = LoggerFactory.getLogger(RequestHandlerTCP.class);
	// The future response which is currently be waited for
	final private FutureResponse futureResponse;
	// The node this request handler is associated with
	final private PeerBean peerBean;
	final private ConnectionBean connectionBean;
	final private Message message;
	final private MessageID sendMessageID;

	public RequestHandlerTCP(PeerBean peerBean, ConnectionBean connectionBean, Message message)
	{
		this(new FutureResponse(message), peerBean, connectionBean, message);
	}

	/**
	 * 
	 * @param objectHolder the bean representing the node this handler belongs
	 *        to
	 */
	public RequestHandlerTCP(FutureResponse futureResponse, PeerBean peerBean,
			ConnectionBean connectionBean, Message message)
	{
		this.peerBean = peerBean;
		this.connectionBean = connectionBean;
		this.futureResponse = futureResponse;
		this.message = message;
		this.sendMessageID = new MessageID(message);
	}

	public FutureResponse getFutureResponse()
	{
		return futureResponse;
	}

	public FutureResponse sendTCP()
	{
		connectionBean.getSender().sendTCP(this, futureResponse, message);
		return futureResponse;
	}

	public FutureResponse sendTCP(String channelName)
	{
		connectionBean.getSender().sendTCP(channelName, this, futureResponse, message);
		return futureResponse;
	}

	public FutureResponse fireAndForgetTCP()
	{
		connectionBean.getSender().sendTCP(null, futureResponse, message);
		return futureResponse;
	}

	protected PeerMap getPeerMap()
	{
		return peerBean.getPeerMap();
	}

	public void messageReceived(Message message) throws Exception
	{
		try
		{
			MessageID recvMessageID = new MessageID(message);
			if (message.getType() == Message.Type.UNKNOWN_ID)
			{
				String msg = "Message was not delivered successfully: " + this.message;
				futureResponse.setFailed(msg);
				getPeerMap().peerOffline(futureResponse.getRequest().getRecipient(), true);
				throw new PeerException(PeerException.AbortCause.PEER_ABORT, msg);
			}
			else if (message.getType() == Message.Type.EXCEPTION)
			{
				String msg = "Message caused an exception on the other side, handle as peer_abort: "
						+ this.message;
				futureResponse.setFailed(msg);
				// getPeerMap().peerOffline(futureResponse.getRequest().getRecipient(),
				// true);
				throw new PeerException(PeerException.AbortCause.PEER_ABORT, msg);
			}
			else if (!sendMessageID.equals(recvMessageID))
			{
				String msg = "Message [" + message
						+ "] sent to the node is not the same as we expect. We sent ["
						+ this.message + "]";
				if (logger.isWarnEnabled())
					logger.warn(msg);
				futureResponse.setFailed(msg);
				// getPeerMap().peerOffline(futureResponse.getRequest().getRecipient(),
				// true);
				throw new PeerException(PeerException.AbortCause.PEER_ABORT, msg);
			}
			else
			{
				if (logger.isDebugEnabled())
					logger.debug("perfect: " + message);
				// We got a good answer, let's mark the sender as alive
				if (message.isOk() || message.isNotOk())
					getPeerMap().peerOnline(message.getSender(), null);
				futureResponse.setResponse(message);
			}
		}
		finally
		{
			futureResponse.setFailed("very unexpected exception...");
		}
	}
}