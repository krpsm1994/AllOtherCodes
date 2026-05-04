-- ─────────────────────────────────────────────────────────────────────────────
-- AlgoTrading – database schema
-- Hibernate (hbm2ddl.auto=update) manages this automatically on startup.
-- This file is provided as a reference / manual creation script.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS algo_trading
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE algo_trading;

-- Table 1: instruments
CREATE TABLE IF NOT EXISTS instruments (
    token    VARCHAR(50)  NOT NULL,
    name     VARCHAR(255) NOT NULL,
    exchange VARCHAR(50)  NOT NULL,
    lotSize  INT          NOT NULL DEFAULT 1,
    type     VARCHAR(50)  NOT NULL DEFAULT 'FUTURE',
    PRIMARY KEY (token)
);

-- Table 2: candles
CREATE TABLE IF NOT EXISTS candles (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    token    VARCHAR(50)  NOT NULL,
    name     VARCHAR(255) NOT NULL,
    date     VARCHAR(50)  NOT NULL,
    open     DOUBLE       NOT NULL DEFAULT 0,
    high     DOUBLE       NOT NULL DEFAULT 0,
    low      DOUBLE       NOT NULL DEFAULT 0,
    close    DOUBLE       NOT NULL DEFAULT 0,
    closeAlt DOUBLE       NOT NULL DEFAULT 0,
    openAlt  DOUBLE       NOT NULL DEFAULT 0,
    type     VARCHAR(10)  NOT NULL DEFAULT '10Min',
    PRIMARY KEY (id),
    INDEX idx_candle_token (token),
    INDEX idx_candle_date  (date)
);

-- Table 3: trades
CREATE TABLE IF NOT EXISTS trades (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token       VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    date        VARCHAR(50)  NOT NULL,
    buyPrice    DOUBLE       NOT NULL,
    sellPrice   DOUBLE       NOT NULL,
    status      VARCHAR(255) NOT NULL DEFAULT 'WATCHING',
    noOfShares  INT          NOT NULL,
    pnl         DOUBLE,
    buyOrderId  VARCHAR(255) DEFAULT NULL,
    sellOrderId VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_trade_token  (token),
    INDEX idx_trade_status (status)
);

-- Table 5: users
CREATE TABLE IF NOT EXISTS users (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    status   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_username (username)
);

-- Table 4: settings
CREATE TABLE IF NOT EXISTS settings (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_name VARCHAR(255),
    `key`      VARCHAR(255),
    value      TEXT,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settings_key_group (`key`, group_name)
);
