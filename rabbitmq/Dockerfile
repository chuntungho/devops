# https://hub.docker.com/_/rabbitmq
FROM rabbitmq:3.8-management

# https://github.com/rabbitmq/rabbitmq-delayed-message-exchange
ADD https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/3.8.17/rabbitmq_delayed_message_exchange-3.8.17.8f537ac.ez /opt/rabbitmq/plugins/

COPY enabled_plugins /etc/rabbitmq
COPY rabbitmq.conf /etc/rabbitmq

RUN chmod 666 /etc/rabbitmq/*