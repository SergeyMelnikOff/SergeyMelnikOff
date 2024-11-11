﻿## Описание

Разработка локального поискового движка по сайту. Приложение работает с локально установленной базой данных MySQL, имеет простой веб-интерфейс и API для управления и получения результатов поисковых запросов.

## Принципы работы

1. **Конфигурация:** В конфигурационном файле application.yaml задаются адреса сайтов для индексации.
2. **Индексация:** Поисковый движок обходит и индексирует все страницы заданных сайтов.
3. **Запросы:** Пользователь отправляет запрос через API, который преобразовывается в список слов в базовой форме.
4. **Поиск:** Поисковый движок ищет страницы, содержащие все слова из запроса.
5. **Ранжирование:** Результаты поиска сортируются и возвращаются пользователю.
6. **Вывод результатов:** Результаты поиска можно просматривать постранично.

## Технологии

- **Spring Boot**
- **Maven**
- **MySQL**
- **Thymeleaf**
- **Lombok**

## Особенности

- **Лемматизация:** Используется для приведения слов к их базовой форме.
- **Мультиязычность:** Используется русскоязычный и англоязычный морфологический разбор.
- **Ранжирование результатов:** Реализована система ранжирования для выдачи наиболее релевантных страниц.
- **Вывод результатов:** Результаты поиска можно просматривать постранично.

## Требования

- **JDK 11+**
- **Maven**
- **MySQL**

## Шаги для локального запуска

1. **Настройка базы данных:**
    - Необходимо установить MySQL.
    - Создать базу данных:
      CREATE DATABASE search_engine;

2. **Настройка конфигурации:**
    - В корне проекта файл `application.yaml`

3. **Открытие в браузере:**
    - В браузере адрес: [http://localhost:8080](http://localhost:8080).

## Структура проекта

- **src/main/java/searchengine**: Все классы проекта.
- **src/main/resources/templates**: HTML-шаблон.
- **src/main/resources/static/assets**: Ресурсы (шрифты, изображения, скрипты).
- **src/main/application.yaml**: Конфигурационный файл.

