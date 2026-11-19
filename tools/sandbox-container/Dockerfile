# syntax=docker/dockerfile:1

FROM node:20-bookworm-slim AS node

FROM maven:3.9-eclipse-temurin-8

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=Asia/Shanghai \
    EDITOR=vim

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      build-essential cmake pkg-config autoconf automake libtool make gdb \
      libssl-dev zlib1g-dev libffi-dev libsqlite3-dev libreadline-dev \
      libbz2-dev liblzma-dev libncurses-dev libgdbm-dev \
      python3 python3-pip python3-venv python3-dev pipx \
      git git-lfs openssh-client rsync curl wget ca-certificates tini \
      jq less vim nano tmux tree htop ncdu lsof strace file shellcheck \
      ripgrep fd-find bat fzf \
      unzip zip xz-utils tar p7zip-full \
      net-tools iproute2 dnsutils traceroute telnet socat \
      default-mysql-client postgresql-client redis-tools sqlite3 \
      gh procps xvfb fonts-noto-cjk \
      libnss3 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 libxkbcommon0 \
      libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 libasound2t64 \
 && ln -sf /usr/bin/fdfind /usr/local/bin/fd \
 && ln -sf /usr/bin/batcat /usr/local/bin/bat \
 && rm -rf /var/lib/apt/lists/*

COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -sf ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && ln -sf ../lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
 && node --version && npm --version

RUN pip install --break-system-packages --no-cache-dir uv \
 && npm install -g pnpm typescript tsx opencode-ai@1.18.32 playwright@1.65.1 \
 && playwright install --with-deps chromium \
 && rm -rf /var/lib/apt/lists/* /tmp/*

ENV PATH=/opt/java/openjdk/bin:/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

COPY start.sh /usr/local/bin/start.sh
RUN chmod +x /usr/local/bin/start.sh

WORKDIR /workspace
EXPOSE 8081 4097
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/start.sh"]
