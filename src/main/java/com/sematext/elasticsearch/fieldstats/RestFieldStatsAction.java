/*
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
 */
package com.sematext.elasticsearch.fieldstats;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.action.RestActions.buildBroadcastShardsHeader;

import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestBuilderListener;

import java.io.IOException;
import java.util.Map;

public class RestFieldStatsAction extends BaseRestHandler {
  public RestFieldStatsAction(Settings settings, RestController controller) {
    controller.registerHandler(GET, "/_field_stats", this);
    controller.registerHandler(POST, "/_field_stats", this);
    controller.registerHandler(GET, "/{index}/_field_stats", this);
    controller.registerHandler(POST, "/{index}/_field_stats", this);
  }

  @Override public String getName() {
    return "field_stats_action";
  }

  @Override
  public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
    if (request.hasContentOrSourceParam() && request.hasParam("fields")) {
      throw new IllegalArgumentException("can't specify a request body and [fields] request parameter, " +
          "either specify a request body or the [fields] request parameter");
    }

    FieldStatsRequestBuilder fieldStatsRequestBuilder = new FieldStatsRequestBuilder(client, FieldStatsAction.INSTANCE);
    final FieldStatsRequest fieldStatsRequest = fieldStatsRequestBuilder.request();
    fieldStatsRequest.indices(Strings.splitStringByCommaToArray(request.param("index")));
    fieldStatsRequest.indicesOptions(IndicesOptions.fromRequest(request, fieldStatsRequest.indicesOptions()));
    fieldStatsRequest.level(request.param("level", FieldStatsRequest.DEFAULT_LEVEL));
    if (request.hasContentOrSourceParam()) {
      try (XContentParser parser = request.contentOrSourceParamParser()) {
        fieldStatsRequest.source(parser);
      }
    } else {
      fieldStatsRequest.setFields(Strings.splitStringByCommaToArray(request.param("fields")));
    }



    return channel -> fieldStatsRequestBuilder.execute(new RestBuilderListener<FieldStatsResponse>(channel) {
      @Override
      public RestResponse buildResponse(FieldStatsResponse response, XContentBuilder builder) throws Exception {
        builder.startObject();
        buildBroadcastShardsHeader(builder, request, response);

        builder.startObject("indices");
        for (Map.Entry<String, Map<String, FieldStats<?>>> entry1 :
            response.getIndicesMergedFieldStats().entrySet()) {
          builder.startObject(entry1.getKey());
          builder.startObject("fields");
          for (Map.Entry<String, FieldStats<?>> entry2 : entry1.getValue().entrySet()) {
            builder.field(entry2.getKey());
            entry2.getValue().toXContent(builder, request);
          }
          builder.endObject();
          builder.endObject();
        }
        builder.endObject();
        if (response.getConflicts().size() > 0) {
          builder.startObject("conflicts");
          for (Map.Entry<String, String> entry : response.getConflicts().entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
          }
          builder.endObject();
        }
        builder.endObject();
        return new BytesRestResponse(RestStatus.OK, builder);
      }
    });
  }
}
