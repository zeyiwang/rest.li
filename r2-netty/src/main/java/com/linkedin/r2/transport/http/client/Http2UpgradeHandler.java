/*
   Copyright (c) 2016 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

/**
 * $Id: $
 */

package com.linkedin.r2.transport.http.client;

import com.linkedin.r2.transport.common.bridge.common.RequestWithCallback;
import com.linkedin.r2.transport.common.bridge.common.TransportCallback;
import com.linkedin.r2.transport.common.bridge.common.TransportResponseImpl;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A handler that triggers the clear text upgrade to HTTP/2 upon adding to pipeline by sending
 * an initial HTTP OPTIONS request with connection upgrade headers. Calls to #write and #flush
 * are suspended util the upgrade is complete. Handler removes itself upon upgrade success.
 *
 * Handler listens to upstream {@link HttpClientUpgradeHandler.UpgradeEvent} event for h2c
 * upgrade success or failure signals. The handler removes itself upon h2c upgrade success and
 * errors out all subsequent requests upon upgrade failure.
 */
class Http2UpgradeHandler extends ChannelDuplexHandler
{
  private static final Logger LOG = LoggerFactory.getLogger(Http2UpgradeHandler.class);

  private final String _host;
  private final int _port;
  private final String _path;

  private ChannelPromise _upgradePromise = null;

  public Http2UpgradeHandler(String host, int port, String path)
  {
    _host = host;
    _port = port;
    _path = path;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception
  {
    _upgradePromise = ctx.channel().newPromise();

    DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, _path);
    request.headers().add(HttpHeaderNames.HOST, _host + ":" + _port);
    ctx.writeAndFlush(request);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
  {
    if (!(msg instanceof RequestWithCallback))
    {
      ctx.write(msg, promise);
      return;
    }

    _upgradePromise.addListener(f -> {
      ChannelFuture future = (ChannelFuture)f;
      if (future.isSuccess())
      {
        ctx.write(msg, promise);
      }
      else
      {
        // Releases the async pool handle
        @SuppressWarnings("unchecked")
        TimeoutAsyncPoolHandle<?> handle = ((RequestWithCallback<?, ?, TimeoutAsyncPoolHandle<?>>) msg).handle();
        handle.error().release();

        // Invokes user specified callback with error
        TransportCallback<?> callback = ((RequestWithCallback) msg).callback();
        callback.onResponse(TransportResponseImpl.error(future.cause()));
      }
    });
  }

  @Override
  public void flush(ChannelHandlerContext ctx) throws Exception
  {
    _upgradePromise.addListener(f -> {
      ChannelFuture future = (ChannelFuture)f;
      if (future.isSuccess())
      {
        ctx.flush();
      }
    });
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
  {
    LOG.debug("Received user event {}", evt);
    if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_ISSUED)
    {
      LOG.debug("HTTP/2 clear text upgrade issued");
    }
    else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL)
    {
      LOG.debug("HTTP/2 clear text upgrade successful");
    }
    else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED)
    {
      LOG.error("HTTP/2 clear text upgrade failed");
      _upgradePromise.setFailure(new IllegalStateException("HTTP/2 clear text upgrade failed"));
    }
    else if (evt == Http2FrameListener.FrameEvent.SETTINGS_FRAME_RECEIVED)
    {
      LOG.debug("HTTP/2 settings frame received");
      // Remove handler from pipeline after upgrade is successful
      ctx.pipeline().remove(this);
      _upgradePromise.setSuccess();
    }
    ctx.fireUserEventTriggered(evt);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
  {
    LOG.error("HTTP/2 clear text upgrade failed", cause);
    _upgradePromise.setFailure(cause);
    ctx.fireExceptionCaught(cause);
  }
}