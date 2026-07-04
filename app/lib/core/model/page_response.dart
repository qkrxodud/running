/// 페이지네이션 공통 래퍼 (conventions.md §6, offset 방식 확정 v0.1.1).
class PageResponse<T> {
  const PageResponse({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  final List<T> items;
  final int page; // 0-base
  final int size;
  final int totalElements;
  final int totalPages;

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) itemFromJson,
  ) =>
      PageResponse(
        items: [
          for (final item in (json['items'] as List<dynamic>? ?? const []))
            itemFromJson(item as Map<String, dynamic>),
        ],
        page: json['page'] as int? ?? 0,
        size: json['size'] as int? ?? 0,
        totalElements: json['total_elements'] as int? ?? 0,
        totalPages: json['total_pages'] as int? ?? 0,
      );
}
