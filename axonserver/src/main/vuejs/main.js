/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

import Vue from 'vue'
import VModal from 'vue-js-modal'
import ComponentInstances from './components/component/Instances.vue';
import ComponentCommands from './components/component/Commands.vue';
import ComponentQueries from './components/component/Queries.vue';
import ComponentProcessors from './components/component/Processors.vue';
import ComponentCommandMetrics from './components/component/CommandMetrics.vue';
import ComponentSubscriptionsMetrics from './components/component/SubscriptionsMetrics.vue';

Vue.use(VModal)

Vue.component('component-instances', ComponentInstances);
Vue.component('component-commands', ComponentCommands);
Vue.component('component-queries', ComponentQueries);
Vue.component('component-processors', ComponentProcessors);
Vue.component('component-command-metrics', ComponentCommandMetrics);
Vue.component('component-subscription-metrics', ComponentSubscriptionsMetrics);

window.Vue = Vue;
