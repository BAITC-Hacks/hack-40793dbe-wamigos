# Third-party notices

Проект использует библиотеки как Maven/Docker/OS-зависимости. Исходный код сторонних приложений в репозиторий не копировался. DOCX-шаблон и логика PDF/DOCX созданы в этом проекте.

| Компонент | Версия | Лицензия | Назначение | Источник |
| --- | --- | --- | --- | --- |
| Spring Boot | 3.5.16 | Apache-2.0 | Web, validation, JPA, Actuator, конфигурация | https://github.com/spring-projects/spring-boot |
| Spring Framework | 6.2.19 | Apache-2.0 | MVC, DI, транзакции, scheduling | https://github.com/spring-projects/spring-framework |
| Hibernate ORM | 6.6.53.Final | LGPL-2.1-or-later | JPA persistence и JSONB mapping | https://github.com/hibernate/hibernate-orm |
| Flyway | 11.7.2 | Apache-2.0 | Миграции PostgreSQL | https://github.com/flyway/flyway |
| PostgreSQL JDBC | 42.7.11 | BSD-2-Clause | JDBC driver | https://github.com/pgjdbc/pgjdbc |
| springdoc-openapi | 2.8.13 | Apache-2.0 | OpenAPI и Swagger UI | https://github.com/springdoc/springdoc-openapi |
| Apache Tika Core | 3.2.3 | Apache-2.0 | Определение media container | https://github.com/apache/tika |
| poi-tl | 1.12.2 | Apache-2.0 | Рендер собственного DOCX-шаблона | https://github.com/Sayi/poi-tl |
| Apache POI | 5.2.5 | Apache-2.0 | Формирование секций и таблиц DOCX | https://github.com/apache/poi |
| Apache PDFBox | 3.0.8 | Apache-2.0 | Формирование PDF и встраивание шрифта | https://github.com/apache/pdfbox |
| easytable | 1.0.2 | MIT | Многостраничная таблица поручений в PDF | https://github.com/vandeseer/easytable |
| Lombok | 1.18.46 | MIT | Генерация builders и constructor boilerplate | https://github.com/projectlombok/lombok |
| Embedded Postgres | 2.2.2 / PostgreSQL binaries 14.22 | Apache-2.0 / PostgreSQL License | Только integration tests без внешней БД | https://github.com/zonkyio/embedded-postgres |
| MockWebServer | 4.12.0 | Apache-2.0 | Только tests HTTP-контракта Python AI service | https://github.com/square/okhttp |
| PostgreSQL container | 17.6-alpine | PostgreSQL License | Локальная runtime DB | https://hub.docker.com/_/postgres |
| Eclipse Temurin JRE | 21 | GPL-2.0-with-classpath-exception | Runtime Docker image | https://github.com/adoptium/temurin-build |
| DejaVu Sans | 2.37, системный пакет | Bitstream Vera and DejaVu licenses | Встраиваемый PDF-шрифт с кириллицей и казахскими буквами | https://dejavu-fonts.github.io |

Транзитивные версии зафиксированы BOM Spring Boot и Maven dependency graph. Полный граф можно получить командой `mvn dependency:tree`.
