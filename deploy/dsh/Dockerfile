ARG NODE_IMAGE=node:24-bookworm-slim@sha256:2fe369e969550cde8e867afc3fe370b260140cab4a23d467074295b42163d553

FROM ${NODE_IMAGE} AS plugin-build
WORKDIR /plugin
COPY integrations/dsh-plugin/package.json integrations/dsh-plugin/package-lock.json ./
RUN npm ci --ignore-scripts && npm cache clean --force
COPY integrations/dsh-plugin/tsconfig.json ./
COPY integrations/dsh-plugin/src ./src
COPY integrations/dsh-plugin/presets ./presets
COPY integrations/dsh-plugin/scripts ./scripts
COPY integrations/dsh-plugin/README.md ./README.md
RUN npm run build && mkdir /artifact && npm pack --pack-destination /artifact

FROM ${NODE_IMAGE} AS node-dependencies
WORKDIR /opt/dsh
RUN apt-get update \
    && apt-get install --yes --no-install-recommends build-essential ca-certificates python3 \
    && rm -rf /var/lib/apt/lists/*
COPY deploy/dsh/package.json deploy/dsh/package-lock.json ./
RUN npm ci --omit=dev \
    && npm cache clean --force
COPY --from=plugin-build /artifact/datascalpel-dsh-plugin-0.5.0.tgz /tmp/dsh-plugin.tgz
RUN mkdir -p node_modules/@datascalpel/dsh-plugin \
    && tar -xzf /tmp/dsh-plugin.tgz --strip-components=1 -C node_modules/@datascalpel/dsh-plugin \
    && node node_modules/@datascalpel/dsh-plugin/scripts/check-runtime.mjs \
    && rm /tmp/dsh-plugin.tgz

FROM ${NODE_IMAGE} AS runtime
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        bash \
        bubblewrap \
        build-essential \
        ca-certificates \
        curl \
        file \
        gcc \
        g++ \
        gh \
        git \
        gzip \
        inetutils-telnet \
        jq \
        less \
        libc6-dev \
        make \
        nano \
        openssh-client \
        patch \
        pkg-config \
        procps \
        python3 \
        python3-dev \
        python3-pip \
        python3-venv \
        ripgrep \
        rsync \
        tar \
        tree \
        unzip \
        vim \
        wget \
        zip \
    && rm -rf /var/lib/apt/lists/* \
    && npm install --global pnpm@11.7.0 \
    && npm cache clean --force

COPY deploy/dsh/requirements.txt /opt/dsh-python-requirements.txt
RUN python3 -m venv /opt/dsh-python \
    && /opt/dsh-python/bin/pip install --no-cache-dir --requirement /opt/dsh-python-requirements.txt \
    && rm -f /usr/local/bin/python /usr/local/bin/python3 /usr/local/bin/pip /usr/local/bin/pip3 \
    && printf '%s\n' '#!/bin/sh' 'exec /opt/dsh-python/bin/python "$@"' > /usr/local/bin/python \
    && printf '%s\n' '#!/bin/sh' 'exec /opt/dsh-python/bin/python "$@"' > /usr/local/bin/python3 \
    && printf '%s\n' '#!/bin/sh' 'exec /opt/dsh-python/bin/python -m pip "$@"' > /usr/local/bin/pip \
    && printf '%s\n' '#!/bin/sh' 'exec /opt/dsh-python/bin/python -m pip "$@"' > /usr/local/bin/pip3 \
    && chmod 0755 /usr/local/bin/python /usr/local/bin/python3 /usr/local/bin/pip /usr/local/bin/pip3 \
    && mkdir -p /opt/dsh /home/node/.dsh /workspace \
    && chown --recursive root:root /home/node/.dsh /workspace \
    && { \
        node --version; \
        npm --version; \
        pnpm --version; \
        python3 --version; \
        /opt/dsh-python/bin/pip --version; \
      } > /opt/dsh/runtime-versions.txt \
    && /opt/dsh-python/bin/pip freeze > /opt/dsh/python-versions.txt

COPY --from=node-dependencies /opt/dsh /opt/dsh
RUN node -p "'@deepseek-ai/dsh@' + require('/opt/dsh/node_modules/@deepseek-ai/dsh/package.json').version" >> /opt/dsh/runtime-versions.txt

ENV DSH_HOME=/home/node/.dsh \
    DSH_PERMISSION_MODE=workspace-write \
    PATH=/opt/dsh-python/bin:/opt/dsh/node_modules/.bin:$PATH \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /workspace
USER root
EXPOSE 13080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=4 \
    CMD node -e "const socket=require('node:net').connect(13080,'127.0.0.1');socket.setTimeout(4000);socket.once('connect',()=>{socket.end();process.exit(0)});socket.once('timeout',()=>{socket.destroy();process.exit(1)});socket.once('error',()=>process.exit(1))"
ENTRYPOINT ["dsh"]
CMD ["web", "--patch", "/opt/dsh-config/docker.cordis.patch.yml", "--no-open"]
