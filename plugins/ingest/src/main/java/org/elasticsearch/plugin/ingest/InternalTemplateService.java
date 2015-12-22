/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugin.ingest;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.ingest.TemplateService;
import org.elasticsearch.script.*;

import java.util.Collections;
import java.util.Map;

class InternalTemplateService implements TemplateService {

    public static final ScriptContext.Plugin INGEST_SCRIPT_CONTEXT = new ScriptContext.Plugin("elasticsearch-ingest", "ingest");

    private final ScriptService scriptService;

    InternalTemplateService(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @Override
    public Template compile(String template) {
        int mustacheStart = template.indexOf("{{");
        int mustacheEnd = template.indexOf("}}");
        if (mustacheStart != -1 && mustacheEnd != -1 && mustacheStart < mustacheEnd) {
            Script script = new Script(template, ScriptService.ScriptType.INLINE, "mustache", Collections.emptyMap());
            CompiledScript compiledScript = scriptService.compile(
                script,
                INGEST_SCRIPT_CONTEXT,
                null /* we can supply null here, because ingest doesn't use indexed scripts */,
                Collections.emptyMap()
            );
            return new Template() {
                @Override
                public String execute(Map<String, Object> model) {
                    ExecutableScript executableScript = scriptService.executable(compiledScript, model);
                    Object result = executableScript.run();
                    if (result instanceof BytesReference) {
                        return ((BytesReference) result).toUtf8();
                    }
                    return String.valueOf(result);
                }

                @Override
                public String getKey() {
                    return template;
                }
            };
        } else {
            return new StringTemplate(template);
        }
    }

    class StringTemplate implements Template {

        private final String value;

        public StringTemplate(String value) {
            this.value = value;
        }

        @Override
        public String execute(Map<String, Object> model) {
            return value;
        }

        @Override
        public String getKey() {
            return value;
        }
    }
}