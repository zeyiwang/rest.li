/*
   Copyright (c) 2012 LinkedIn Corp.

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

package com.linkedin.restli.internal.server.response;


import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.restli.common.HttpStatus;
import com.linkedin.restli.internal.server.RoutingResult;
import com.linkedin.restli.server.RestLiResponseData;
import com.linkedin.restli.server.RestLiServiceException;
import com.linkedin.restli.server.UpdateResponse;

import java.net.HttpCookie;
import java.util.List;
import java.util.Map;


public class UpdateResponseBuilder implements RestLiResponseBuilder
{
  @Override
  public PartialRestResponse buildResponse(RoutingResult routingResult, RestLiResponseData responseData)
  {
    return new PartialRestResponse.Builder().headers(responseData.getHeaders())
                                            .cookies(responseData.getCookies())
                                            .status(responseData.getStatus())
                                            .build();
  }

  @Override
  public RestLiResponseData buildRestLiResponseData(RestRequest request, RoutingResult routingResult,
                                                    Object result, Map<String, String> headers,
                                                    List<HttpCookie> cookies)
  {
    UpdateResponse updateResponse = (UpdateResponse) result;
    //Verify that the status in the UpdateResponse is not null. If so, this is a developer error.
    if (updateResponse.getStatus() == null)
    {
      throw new RestLiServiceException(HttpStatus.S_500_INTERNAL_SERVER_ERROR,
          "Unexpected null encountered. HttpStatus is null inside of a UpdateResponse returned by the resource method: "
              + routingResult.getResourceMethod());
    }

    RestLiResponseDataImpl responseData = new RestLiResponseDataImpl(updateResponse.getStatus(), headers, cookies);
    responseData.setResponseEnvelope(new UpdateResponseEnvelope(responseData));

    return responseData;
  }
}
