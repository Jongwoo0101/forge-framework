from pathlib import Path

# 프로젝트 설정
PROJECT_ROOT = Path("/Users/wonjong-u/Documents/Java Projects/forge-framework")

SRC_ROOT = PROJECT_ROOT / "src" / "main" / "java" / "forgeframework"

OUTPUT = PROJECT_ROOT / "forgeframework_source_dump.md"

# Java 파일 수집
java_files = sorted(SRC_ROOT.rglob("*.java"))

with OUTPUT.open("w", encoding="utf-8") as out:

    out.write("# ForgeFramework Source Dump\n\n")
    out.write(f"총 Java 파일 수 : **{len(java_files)}개**\n\n")
    out.write("---\n\n")

    # 목차
    out.write("## Files\n\n")

    for file in java_files:
        rel = file.relative_to(SRC_ROOT)
        out.write(f"- `{rel}`\n")

    out.write("\n---\n\n")

    # 파일 내용
    for idx, file in enumerate(java_files, start=1):

        rel = file.relative_to(SRC_ROOT)

        out.write(f"# {idx}. {rel}\n\n")

        out.write(f"**Path**\n")
        out.write(f"`src/main/java/forgeframework/{rel}`\n\n")

        out.write("```java\n")

        try:
            source = file.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            source = file.read_text(
                encoding="utf-8",
                errors="replace"
            )

        out.write(source)

        if not source.endswith("\n"):
            out.write("\n")

        out.write("```\n\n")

        out.write("---\n\n")

print()
print("완료!")
print(f"총 {len(java_files)}개의 Java 파일을 저장했습니다.")
print(f"출력 파일 : {OUTPUT}")