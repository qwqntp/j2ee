# Danh sách skill khả dụng trong OpenCode

> Ngày ghi: 2026-11-22  
> Nguồn: danh sách skill được OpenCode nạp trong session này, đối chiếu với các thư mục trên đĩa.  
> Tổng: **51 skill**.

---

## 1. Nguồn của các skill

| Nguồn | Đường dẫn | Số lượng |
|---|---|---|
| Global skills | `~/.config/opencode/skills/` | 35 |
| Plugin superpowers | `~/.config/opencode/node_modules/superpowers/skills/` | 15 (trùng 1 với global: `test-driven-development`) |
| Built-in OpenCode | không nằm trong thư mục skill (hiện diện sẵn) | 2 (`opencode`, `report`) |

> Ghi chú: `test-driven-development` tồn tại ở cả hai thư mục global và superpowers; OpenCode chỉ nạp một tên.

---

## 2. Global skills — `~/.config/opencode/skills/`

| # | Skill | Mục đích ngắn gọn |
|---|---|---|
| 1 | `accessibility-engineering` | Audit WCAG 2.2 AA, axe-core/pa11y, triage lỗi a11y, contrast & screen reader |
| 2 | `api-and-interface-design` | Thiết kế API/public interface, REST/GraphQL, hợp đồng kiểu giữa các module |
| 3 | `banner-design` | Thiết kế banner social/ads/web/print, nhiều hướng art direction |
| 4 | `brand` | Brand voice, visual identity, messaging, kiểm tra tuân thủ thương hiệu |
| 5 | `browser-testing-with-devtools` | Test trình duyệt thật qua Chrome DevTools MCP (DOM, console, network, perf) |
| 6 | `caveman` | Trả lời ngắn gọn kiểu "caveman", bỏ fluff nhưng giữ đủ ý kỹ thuật |
| 7 | `ci-cd-and-automation` | Thiết lập CI/CD, quality gate, test runner trong CI, chiến lược deploy |
| 8 | `code-review-and-quality` | Review code đa chiều trước khi merge (diff/PR do người hoặc agent viết) |
| 9 | `code-simplification` | Đơn giản hóa code, giảm nesting/độ phức tạp mà không đổi hành vi |
| 10 | `constraint-driven-development` | Viết chuẩn chất lượng vào `CONSTRAINTS.md`, ngăn hạ thấp tiêu chuẩn (bỏ test, @ts-ignore,...) |
| 11 | `context-engineering` | Tối ưu context cho agent, cấu hình rules files khi bắt đầu/chuyển task |
| 12 | `debugging-and-error-recovery` | Debug có hệ thống, tìm root cause thay vì đoán (test fail, build break, lỗi lạ) |
| 13 | `deprecation-and-migration` | Deprecate/xóa hệ thống cũ, migrate schema production (expand/contract) |
| 14 | `design` | Bộ công cụ thiết kế tổng hợp: logo, CIP, slides, banner, icon, social photos |
| 15 | `design-system` | Kiến trúc token 3 lớp, component spec, CSS variables, slide theo design system |
| 16 | `documentation-and-adrs` | Viết ADR & tài liệu, phát hiện doc bị lỗi thời so với code |
| 17 | `doubt-driven-development` | Phản biện độc lập mọi quyết định quan trọng trước khi chấp nhận |
| 18 | `frontend-design` | Định hướng thị giác có chủ đích cho UI mới hoặc tái cấu trúc UI |
| 19 | `frontend-ui-engineering` | Xây UI production-ready, responsive, accessible, quản lý state |
| 20 | `git-workflow-and-versioning` | Git workflow, commit/branch/PR, chia commit, semantic version, changelog |
| 21 | `idea-refine` | Làm rõ ý tưởng thô bằng tư duy phân kỳ/hội tụ, stress-test plan |
| 22 | `incremental-implementation` | Giao đổi theo lát mỏng kiểm chứng được, feature flag, tránh viết quá nhiều code cùng lúc |
| 23 | `interview-me` | Phỏng vấn từng câu đến khi hiểu ~95% ý định thật của người dùng |
| 24 | `observability-and-instrumentation` | Thêm logging, metrics, tracing, alerting; giải thích được hành vi production |
| 25 | `performance-optimization` | Đo trước khi tối ưu; N+1, index, cache, Core Web Vitals, budget & verify |
| 26 | `planning-and-task-breakdown` | Chia spec thành các task có thứ tự, ước lượng scope, tìm việc song song |
| 27 | `security-and-hardening` | Chống OWASP Top 10, input handler, auth/session, audit dependency |
| 28 | `shipping-and-launch` | Checklist deploy production, monitor, rollout staged, rollback |
| 29 | `slides` | Tạo HTML presentation chiến lược với Chart.js và design token |
| 30 | `source-driven-development` | Đối chiếu implementation với tài liệu chính thức, tránh pattern lỗi thời |
| 31 | `spec-driven-development` | Viết spec/PRD trước khi code, scope V1/V2, capability map |
| 32 | `test-driven-development` | Chu trình red-green-refactor: viết test fail trước, đổi code sau |
| 33 | `ui-styling` | shadcn/ui + Tailwind, theme/dark mode, responsive, canvas visual |
| 34 | `ui-ux-pro-max` | Trí tuệ thiết kế UI/UX: style, palette, font, icon, guideline, chart |
| 35 | `using-agent-skills` | Meta-skill phát hiện & gọi skill phù hợp |

---

## 3. Skill từ plugin superpowers — `node_modules/superpowers/skills/`

| # | Skill | Mục đích ngắn gọn |
|---|---|---|
| 1 | `brainstorming` | Bắt buộc dùng trước công việc sáng tạo: khám phá ý định, yêu cầu, thiết kế trước khi implement |
| 2 | `diagnosing-superpowers` | Điều tra vì sao phiên superpowers chạy sai (làm lại, bỏ plan, skill không kích hoạt,...) |
| 3 | `dispatching-parallel-agents` | Điều phối 2+ task độc lập cho subagent song song |
| 4 | `executing-plans` | Tự implement plan ngay trong session hiện tại (không dùng subagent) |
| 5 | `finishing-a-development-branch` | Quyết định integrate công việc khi code xong & test xanh |
| 6 | `receiving-code-review` | Tiếp nhận feedback review có kiểm chứng kỹ thuật, không đồng ý mù quáng |
| 7 | `requesting-code-review` | Yêu cầu review khi hoàn thành task/feature lớn hoặc trước merge |
| 8 | `subagent-driven-development` | Thực hiện plan với các task độc lập bằng subagent |
| 9 | `systematic-debugging` | Debug hệ thống khi gặp bug/test fail/hành vi bất thường (dùng trước khi sửa) |
| 10 | `test-driven-development` | (Trùng tên với global) Red-green-refactor, viết test fail trước |
| 11 | `using-git-worktrees` | Tạo workspace cô lập bằng git worktree trước khi làm feature |
| 12 | `using-superpowers` | Meta-skill khởi động: tìm & gọi skill bắt buộc trước mọi phản hồi/hành động |
| 13 | `verification-before-completion` | Chạy lệnh verify & có bằng chứng trước khi tuyên bố xong/fix/pass |
| 14 | `writing-plans` | Viết kế hoạch implement khi đã có spec/yêu cầu, trước khi đụng code |
| 15 | `writing-skills` | Tạo, sửa, kiểm chứng skill trước khi deploy |

---

## 4. Skill built-in

| # | Skill | Mục đích ngắn gọn |
|---|---|---|
| 1 | `opencode` | Mọi câu hỏi về OpenCode: CLI, config, MCP, plugin, API/SDK, troubleshooting (V2) |
| 2 | `report` | Thu thập chẩn đoán và đưa issue/bug lên GitHub CLI cho OpenCode |

---

## 5. Ghi chú

- Danh sách này phản ánh những skill **đang nạp được trong session hiện tại**, không phải mọi skill từng tồn tại.
- Mô tả ở bảng là tóm tắt một dòng; nội dung đầy đủ nằm trong file `SKILL.md` của từng thư mục (đọc bằng công cụ skill của OpenCode).
- Không file code nào bị thay đổi khi tạo tài liệu này.
