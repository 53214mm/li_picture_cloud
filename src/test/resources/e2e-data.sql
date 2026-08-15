INSERT INTO user
    (id, userAccount, userPassword, userName, userRole, isDelete)
VALUES
    (7, 'companion_e2e',
     '$2a$12$a5SNma8tchPGcKIOSIGgI.liyrqFQiARSqUVszUdmlO3qCR9U3Cs6',
     '伙伴端到端用户', 'user', 0),
    (8, 'companion_admin_e2e',
     '$2a$12$a5SNma8tchPGcKIOSIGgI.liyrqFQiARSqUVszUdmlO3qCR9U3Cs6',
     '伙伴端到端管理员', 'admin', 0);

INSERT INTO space
    (id, spaceName, spaceLevel, spaceType, maxSize, maxCount,
     totalSize, totalCount, userId, isDelete, createTime, editTime, updateTime)
VALUES
    (10, '伙伴私有空间', 0, 0, 104857600, 100,
     2048, 1, 7, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO picture
    (id, url, thumbnailUrl, originalUrl, name, introduction, category, tags,
     picSize, picWidth, picHeight, picScale, picFormat, userId, spaceId,
     isDelete, reviewStatus, createTime, editTime, updateTime)
VALUES
    (102, '/images/mosaic/travel.jpg', '/images/mosaic/travel.jpg',
     '/images/mosaic/travel.jpg', '旅行样片', '仅供端到端测试', '旅行',
     '["旅行"]', 2048, 800, 600, 1.3333, 'jpg', 7, 10,
     0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (103, '/images/mosaic/garden.jpg', '/images/mosaic/garden.jpg',
     '/images/mosaic/garden.jpg', '花园样片', '仅供端到端测试', '花园',
     '["花园"]', 2048, 800, 600, 1.3333, 'jpg', 7, 10,
     0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
