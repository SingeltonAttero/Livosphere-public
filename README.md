# Публичные документы Livosphere

Политика конфиденциальности: https://singeltonattero.github.io/Livosphere-public/privacy/

Исходник — `privacy/index.html`. Страница на русском, с системными шрифтами, шириной текста до 48rem, адаптивным заголовком и видимым фокусом ссылок. Нет внешних ресурсов, скриптов, аналитики и форм. Это отдельная веб-страница; интерфейс Android не изменён.

GitHub Pages настроен на ветку `gh-pages`, каталог `/`. В неё публикуется только дерево `site/`, без Android, ресурсов обоев и локальных артефактов. Файл `.nojekyll` отключает обработку Jekyll.

После изменения политики обновите дату, проверьте фактическое поведение приложения, HTML, мобильную вёрстку и `git diff --check`. Закоммитьте изменения в `main`. Push и публикация требуют поручения владельца.

При наличии такого поручения обновление сайта из закоммиченного `site/`:

```sh
git fetch origin gh-pages
pages_commit=$(git commit-tree "$(git rev-parse HEAD:site)" -p "$(git rev-parse origin/gh-pages)" -m "Publish Livosphere documents")
git push origin "$pages_commit:refs/heads/gh-pages"
```

Дождитесь успешного развёртывания GitHub Pages и проверьте публичный URL. Push сам по себе не подтверждает доступность страницы.
