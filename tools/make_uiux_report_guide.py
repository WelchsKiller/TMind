# -*- coding: utf-8 -*-
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.util import Inches, Pt

OUT = r"C:\Jhcha\스튜디오 림보_앱 UI UX 개발 보고서_작성가이드.pptx"

TEAL = RGBColor(0x1A, 0x8A, 0x7A)
TEAL_DARK = RGBColor(0x0F, 0x5C, 0x52)
TEAL_SOFT = RGBColor(0xD7, 0xEF, 0xEA)
INK = RGBColor(0x1F, 0x29, 0x37)
MUTED = RGBColor(0x5B, 0x64, 0x72)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
AMBER = RGBColor(0xB4, 0x53, 0x09)
CREAM = RGBColor(0xF7, 0xF4, 0xEE)
CARD = RGBColor(0xFF, 0xFF, 0xFF)
LINE = RGBColor(0xD6, 0xDE, 0xE6)

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)


def _set_run(run, text, size=16, bold=False, color=INK, font="Pretendard"):
    run.text = text
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = font


def add_box(slide, l, t, w, h, fill, line=None):
    sh = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(l), Inches(t), Inches(w), Inches(h))
    sh.adjustments[0] = 0.08
    sh.fill.solid()
    sh.fill.fore_color.rgb = fill
    sh.line.color.rgb = line or fill
    sh.line.width = Pt(0.75)
    return sh


def add_text(slide, l, t, w, h, text, size=16, bold=False, color=INK, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    box = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(h))
    tf = box.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    _set_run(run, text, size, bold, color)
    return box


def add_para_box(slide, l, t, w, h, lines, size=14, color=INK):
    """lines: list of str or (str, bold)"""
    box = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(h))
    tf = box.text_frame
    tf.word_wrap = True
    first = True
    for item in lines:
        if first:
            p = tf.paragraphs[0]
            first = False
        else:
            p = tf.add_paragraph()
        p.space_after = Pt(6)
        if isinstance(item, tuple):
            text, bold = item
        else:
            text, bold = item, False
        run = p.add_run()
        _set_run(run, text, size, bold, color)
    return box


def new_slide(title, subtitle=None, badge=None):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    # bg
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    bg.fill.solid()
    bg.fill.fore_color.rgb = CREAM
    bg.line.fill.background()
    bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, Inches(0.16), prs.slide_height)
    bar.fill.solid()
    bar.fill.fore_color.rgb = TEAL
    bar.line.fill.background()
    add_text(slide, 0.45, 0.18, 10.5, 0.38, "스튜디오 림보 · TMind(하루마음) UI·UX 개발 보고서 작성 가이드", 12, False, MUTED)
    add_text(slide, 0.45, 0.48, 11.5, 0.45, title, 26, True, TEAL_DARK)
    if subtitle:
        add_text(slide, 0.45, 0.92, 12.2, 0.36, subtitle, 14, False, MUTED)
    if badge:
        b = add_box(slide, 11.15, 0.22, 1.85, 0.36, TEAL)
        add_text(slide, 11.15, 0.24, 1.85, 0.32, badge, 11, True, WHITE, PP_ALIGN.CENTER)
    return slide


# 1 cover
s = new_slide("보고서 빈칸을 이렇게 채우면 됩니다", "원본 PDF 7쪽 구조에 맞춰, 넣을 문안과 자리만 정리한 작성용 자료입니다.")
add_box(s, 0.45, 1.5, 6.0, 5.4, WHITE, LINE)
add_text(s, 0.7, 1.7, 5.5, 0.4, "원본 PDF에서 비어 있는 곳", 16, True, TEAL_DARK)
add_para_box(s, 0.7, 2.2, 5.5, 4.4, [
    ("1쪽  개발 목표", True),
    "기술/사업 목표 본문은 일부만 있고, 설명 칸이 비어 있습니다.",
    ("2쪽  UI·UX 개발 목표", True),
    "직관적 인터페이스 한 줄만 있고, 기술/사용 목표 칸이 비어 있습니다.",
    ("3쪽  개요", True),
    "제목만 있고 본문·구성·대상이 없습니다.",
    ("4쪽  화면 흐름도", True),
    "도식과 단계 설명이 없습니다.",
    ("5~7쪽  화면 상세", True),
    "화면별 목적·주요 문구·인터랙션이 없습니다.",
], 14)
add_box(s, 6.7, 1.5, 6.15, 5.4, TEAL_SOFT, TEAL_SOFT)
add_text(s, 6.95, 1.7, 5.7, 0.4, "이 PPT 사용법", 16, True, TEAL_DARK)
add_para_box(s, 6.95, 2.2, 5.7, 4.4, [
    "노란/민트 카드 = 보고서에 그대로 붙여 넣을 문안",
    "회색 작은 글씨 = 왜 이 칸이 필요한지",
    "화면 이름은 앱 표기(하루마음)와 보고서 표기(TMind)를 함께 씁니다.",
    "문장 톤: 시니어 참여자도 읽기 쉽게, 짧은 문장, 전문 용어는 한 번만 풀어 씁니다.",
    "스크린샷은 홈·측정·설문·일기·결과 5장을 넣으면 충분합니다.",
], 15)

# 2 page map
s = new_slide("원본 7쪽을 이렇게 나눕니다", "각 쪽의 역할만 먼저 정하면 빈 칸이 덜 막막합니다.", "구성")
pages = [
    ("1쪽", "개발 목표", "왜 이 앱을 만드는가\n기술 목표 / 사업 목표"),
    ("2쪽", "UI·UX 목표", "누가, 어떻게 쓰게 할 것인가\n기술 목표 / 사용 목표"),
    ("3쪽", "개요", "한 장으로 보는 제품\n대상·하루 루틴·핵심 가치"),
    ("4쪽", "화면 흐름", "로그인부터 피드백까지\n강제 순서와 분기"),
    ("5쪽", "화면 상세 1", "로그인 / 오늘의 기록"),
    ("6쪽", "화면 상세 2", "심박변이도 / 설문 / 일기"),
    ("7쪽", "화면 상세 3", "결과·피드백 / 추가 측정 / 별"),
]
for i, (no, title, desc) in enumerate(pages):
    x = 0.45 + (i % 7) * 1.82
    y = 1.55
    add_box(s, x, y, 1.7, 3.4, WHITE, LINE)
    add_box(s, x, y, 1.7, 0.55, TEAL)
    add_text(s, x, y + 0.08, 1.7, 0.4, no, 16, True, WHITE, PP_ALIGN.CENTER)
    add_text(s, x + 0.08, y + 0.7, 1.54, 0.7, title, 15, True, TEAL_DARK, PP_ALIGN.CENTER)
    add_text(s, x + 0.1, y + 1.5, 1.5, 1.7, desc, 12, False, MUTED, PP_ALIGN.CENTER)

add_text(s, 0.45, 5.2, 12.4, 1.8,
         "작성 팁: 1~2쪽은 ‘목표 문장’, 3쪽은 ‘한 장 요약’, 4쪽은 ‘화살표 도식’, 5~7쪽은 ‘화면 캡처 + 3줄 설명’으로 채우면 보고서 밀도가 맞습니다.\n"
         "아래부터는 쪽마다 붙여넣기용 문안입니다.",
         14, False, INK)

# 3 page1
s = new_slide("1쪽 · 개발 목표 — 넣을 문안", "이미 있는 큰 문장 아래에, 기술/사업 칸을 이렇게 채우면 됩니다.", "1쪽")
add_box(s, 0.45, 1.45, 12.4, 0.85, TEAL_SOFT)
add_text(s, 0.65, 1.52, 12.0, 0.7,
         "이미 있는 문장: 자율신경 이상 탐지 기술의 고도화 및 개인의 일상 건강 관리와 의료진의 임상 진료를 포괄하는 전주기적 헬스케어 생태계 완성",
         14, False, TEAL_DARK)

add_box(s, 0.45, 2.5, 6.05, 4.5, WHITE, LINE)
add_text(s, 0.65, 2.65, 5.7, 0.4, "기술적 목표 (붙여넣기)", 16, True, TEAL_DARK)
add_para_box(s, 0.65, 3.15, 5.7, 3.6, [
    "• ECG 패치로 5분 심박변이도(HRV)를 안정적으로 수집한다.",
    "• 유효 측정만 서버에 올리고, 패치 미부착·무신호는 재측정으로 돌린다.",
    "• 설문(EMA)·마음일기·예측 좌표를 같은 세션에 묶어 저장한다.",
    "• 참여자가 따로 동의를 누르지 않아도, 로그인 토큰만으로 연구 API를 쓴다.",
    "• 오전/오후/추가 측정 상태를 /today 한 번에 맞춰 홈을 그린다.",
], 14)

add_box(s, 6.75, 2.5, 6.1, 4.5, WHITE, LINE)
add_text(s, 6.95, 2.65, 5.7, 0.4, "사업적 목표 (붙여넣기)", 16, True, TEAL_DARK)
add_para_box(s, 6.95, 3.15, 5.7, 3.6, [
    "• 임상 현장에서 설명 없이 바로 쓸 수 있는 시니어용 앱을 만든다.",
    "• 하루 2회(오전·오후) 반복 측정으로 연구 데이터를 쌓는다.",
    "• 추가 측정으로 하루 안 변화까지 남긴다.",
    "• 개인은 ‘오늘 마음’을 보고, 연구·의료진은 주간 추이와 별 기록으로 참여를 본다.",
    "• 앱 이름(하루마음)은 친근하게, 보고서 명칭(TMind)은 연구 브랜드로 쓴다.",
], 14)

# 4 page2
s = new_slide("2쪽 · UI·UX 개발 목표 — 넣을 문안", "이미 있는 ‘직관적인 인터페이스’ 문장 아래 두 칸을 채웁니다.", "2쪽")
add_box(s, 0.45, 1.45, 12.4, 0.7, TEAL_SOFT)
add_text(s, 0.65, 1.55, 12.0, 0.5,
         "이미 있는 문장: 누구나 별다른 설명 없이 사용할 수 있는 직관적인 인터페이스 설계 및 디자인",
         14, False, TEAL_DARK)

add_box(s, 0.45, 2.35, 6.05, 4.65, WHITE, LINE)
add_text(s, 0.65, 2.5, 5.7, 0.4, "기술적 목표 (UI 구현)", 16, True, TEAL_DARK)
add_para_box(s, 0.65, 3.0, 5.7, 3.8, [
    "• 큰 글씨, 큰 버튼, 읽어주기(TTS)를 모든 주요 화면에 둔다.",
    "• 홈 문구는 오전/오후 진행 상태에 따라 바뀐다.",
    "• 미션은 심박변이도 → 설문 → 마음일기 순서로만 진행한다.",
    "• 연결·측정 실패는 숫자 status와 짧은 안내로 보여 준다.",
    "• 별은 오전=왼쪽, 오후=오른쪽, 추가 측정=특수 효과로 구분한다.",
], 14)

add_box(s, 6.75, 2.35, 6.1, 4.65, WHITE, LINE)
add_text(s, 6.95, 2.5, 5.7, 0.4, "사용적 목표 (경험)", 16, True, TEAL_DARK)
add_para_box(s, 6.95, 3.0, 5.7, 3.8, [
    "• 8자리 번호만 누르면 바로 오늘의 기록으로 들어간다.",
    "• ‘지금 무엇을 하면 되는지’가 상단 한 줄로 보인다.",
    "• 측정 중에는 시간·연결 상태·다음 단계만 보여 주의를 흩뜨리지 않는다.",
    "• 잘못된 측정은 저장하지 않고, 다시 하면 된다는 안심을 준다.",
    "• 결과를 본 뒤에는 바꿀 수 없게 해, 연구 데이터가 흔들리지 않게 한다.",
], 14)

# 5 overview
s = new_slide("3쪽 · 개요 — 한 장 요약 문안", "제목만 있는 페이지입니다. 아래 네 덩어리를 칸에 나눠 넣으면 됩니다.", "3쪽")
blocks = [
    ("제품", "TMind(하루마음)는 임상·연구 참여자가 ECG 패치로 심박변이도를 재고, 마음 상태 설문과 음성 일기를 남기는 앱입니다. 서버가 정한 오늘 세션(/today)이 홈의 기준입니다."),
    ("대상", "중장년·시니어 연구 참여자, 임상 태블릿(Android 13)과 개인 휴대폰. 설명 인력 없이 혼자 또는 간단한 안내만으로 씁니다."),
    ("하루 루틴", "오전 미션 3단계 → (필요 시) 오후 미션 3단계 → 조건이 되면 추가 측정. 각 단계는 심박변이도 5분 → 마음 상태 설문 → 마음일기입니다."),
    ("핵심 원칙", "한 화면에 한 행동. 실패해도 다시 하면 된다. 유효한 측정만 남긴다. 예측·피드백이 끝난 세션은 잠근다."),
]
for i, (h, body) in enumerate(blocks):
    x = 0.45 + (i % 2) * 6.4
    y = 1.5 + (i // 2) * 2.7
    add_box(s, x, y, 6.15, 2.5, WHITE, LINE)
    add_box(s, x, y, 6.15, 0.48, TEAL)
    add_text(s, x + 0.2, y + 0.06, 5.7, 0.36, h, 16, True, WHITE)
    add_text(s, x + 0.2, y + 0.65, 5.75, 1.7, body, 14, False, INK)

# 6 flow
s = new_slide("4쪽 · 화면 흐름도 — 이 순서로 그리면 됩니다", "박스를 가로로 놓고 화살표만 그어도 보고서용 흐름도가 됩니다.", "4쪽")
steps = [
    ("1", "스플래시", "오늘의 마음을\n천천히 정리해 보아요"),
    ("2", "로그인", "8자리 번호\n로그인 토큰으로 바로 사용"),
    ("3", "오늘의 기록", "상단 안내 + 별 7칸\n3개 미션 카드"),
    ("4", "심박변이도", "안내 → 연결 → 5분\n유효할 때만 저장"),
    ("5", "마음 상태", "안내 → 설문\n서버 문항"),
    ("6", "마음일기", "음성 녹음\n저장 후 예측"),
    ("7", "결과·피드백", "사분면 확인\n일치/다름/모름"),
]
for i, (no, title, desc) in enumerate(steps):
    x = 0.4 + i * 1.84
    add_box(s, x, 1.5, 1.72, 2.55, WHITE, LINE)
    add_box(s, x, 1.5, 1.72, 0.42, TEAL)
    add_text(s, x, 1.54, 1.72, 0.34, no + "  " + title, 12, True, WHITE, PP_ALIGN.CENTER)
    add_text(s, x + 0.08, 2.05, 1.56, 1.8, desc, 12, False, INK, PP_ALIGN.CENTER)
    if i < 6:
        add_text(s, x + 1.55, 2.4, 0.35, 0.4, "→", 20, True, TEAL, PP_ALIGN.CENTER)

add_box(s, 0.45, 4.25, 12.4, 2.75, WHITE, LINE)
add_text(s, 0.65, 4.4, 12.0, 0.35, "흐름도에 꼭 적을 분기 (빈 칸에 작은 주석으로)", 15, True, TEAL_DARK)
add_para_box(s, 0.65, 4.85, 12.0, 2.0, [
    "• 측정 실패(패치 없음/무신호) → 서버 저장 없음 → 재측정. 설문으로 가지 않음.",
    "• 오전 미완료 후 오후 접속 → 상단: “오전 미션을 놓치셨어요. 오후 미션을 시작해 주세요.”",
    "• 오전·오후 중 1개 완료 + 예측·피드백 후 → 추가 측정 버튼 활성화.",
    "• 예측이 저장된 세션 → HRV·설문·일기 신규 입력 잠금. 같은 시각 재전송만 허용.",
    "• 연구 기간 종료 후에도 /today가 FOLLOWUP을 주면 홈에서 이어감.",
], 13)

# 7 home detail
s = new_slide("5쪽 · 화면 상세 — 로그인 / 오늘의 기록", "캡처 옆에 ‘목적 / 주요 문구 / 동작’ 세 줄만 적어도 상세 페이지가 됩니다.", "5쪽")
cards = [
    ("로그인",
     "목적: 연구 번호를 눌러 참여자를 연다.\n주요 문구: “받으신 8자리 번호를 눌러주세요.”\n동작: 확인 → 로그인 API → 토큰 저장 → 홈.\n빈 칸 팁: 키패드 스크린샷 + TTS 버튼 표시."),
    ("오늘의 기록 · 상단",
     "목적: 지금 할 일을 한 줄로 알린다.\n오전 시작 / 오후 시작 / 오전 놓침 / 둘 다 완료.\n동작: /today currentType, missedType, 완료 여부로 문구 결정.\n빈 칸 팁: 네 가지 문구를 표로 넣기."),
    ("오늘의 기록 · 미션",
     "목적: 세 장을 순서대로 누르게 한다.\n카드: 심박변이도 / 마음 상태 / 마음일기.\n동작: 앞 단계 미완료 시 안내 토스트. 완료 카드는 체크.\n빈 칸 팁: 진행 바 “오전 미션 0/3 완료”를 같이 찍기."),
    ("오늘의 기록 · 별",
     "목적: 7일 참여를 한눈에 보여 준다.\n오전만 왼쪽, 오후만 오른쪽, 둘 다 가득, 추가 측정은 금색 효과.\n동작: /today stars + 참여 현황 amDone/pmDone.\n빈 칸 팁: 별 5종을 한 줄로 나열."),
]
for i, (h, body) in enumerate(cards):
    x = 0.45 + (i % 2) * 6.4
    y = 1.5 + (i // 2) * 2.7
    add_box(s, x, y, 6.15, 2.5, WHITE, LINE)
    add_text(s, x + 0.2, y + 0.12, 5.7, 0.35, h, 16, True, TEAL_DARK)
    add_text(s, x + 0.2, y + 0.55, 5.75, 1.8, body, 13, False, INK)

# 8 measure
s = new_slide("6쪽 · 화면 상세 — 측정 / 설문 / 일기", "임상 태블릿에서 가장 많이 보는 세 화면입니다. 실패 동선을 꼭 적으세요.", "6쪽")
cards = [
    ("심박변이도 안내",
     "목적: 패치·블루투스·5분 정지를 미리 알린다.\n문구: 패치 부착, 블루투스 켜기, 말하지 않기.\n동작: 측정 시작만 있다. 건너뛰기 없음."),
    ("심박변이도 측정",
     "목적: 연결을 확인하고 5분을 잰다.\n원 안: 시간만. 원 밖: 연결 상태 문장.\n아래 고정 안내: 측정→설문→일기.\n실패: 전원/거리 안내, 카운트 시작 안 함."),
    ("심박변이도 결과",
     "목적: 유효할 때만 확인하고 저장한다.\n유효: 확인 → 서버 저장 → 설문.\n무효(--): 재측정. 서버 미전송.\n빈 칸 팁: 유효/무효 화면을 나란히 넣기."),
    ("마음 상태 · 마음일기",
     "설문: “지금 마음 상태를 알려 주세요.” 서버 문항, 유효 HRV 후에만.\n일기: “오늘 하루를 말씀해 주세요.” 저장 후 예측·결과.\n재측정 시 설문·일기는 처음부터."),
]
for i, (h, body) in enumerate(cards):
    x = 0.45 + (i % 2) * 6.4
    y = 1.5 + (i // 2) * 2.7
    add_box(s, x, y, 6.15, 2.5, WHITE, LINE)
    add_text(s, x + 0.2, y + 0.12, 5.7, 0.35, h, 16, True, TEAL_DARK)
    add_text(s, x + 0.2, y + 0.55, 5.75, 1.8, body, 13, False, INK)

# 9 result
s = new_slide("7쪽 · 화면 상세 — 결과 / 추가 측정 / 마감", "마지막 쪽은 ‘끝난 뒤’를 보여 주면 보고서가 닫힙니다.", "7쪽")
cards = [
    ("분석 결과 · 사분면",
     "목적: 예측된 마음 위치를 보여 준다.\n동작: HRV+설문+일기가 모두 끝난 뒤에만 연다.\n빈 칸 팁: 사분면 캡처와 “일치해요 / 달라요 / 모르겠어요” 버튼을 함께."),
    ("피드백",
     "목적: 예측과 주관이 맞는지 남긴다.\n일치·모름: 좌표 없이 제출.\n다름: 사분면에서 고른 좌표만 제출. reasonCode 없음."),
    ("추가 측정",
     "목적: 정규 미션 이후 한 번 더 잰다.\n조건: 오늘 오전 또는 오후 세션 완료.\n하루 5회, 보류로 끝난 측정도 포함.\n별에는 금색 특수 효과."),
    ("끝내는 경험",
     "로그아웃은 인사 줄 옆.\n미완료 종료 시 확인 창.\n7일 채우면 정서 추이 버튼.\nFOLLOWUP이 오면 연구 종료 후에도 이어서."),
]
for i, (h, body) in enumerate(cards):
    x = 0.45 + (i % 2) * 6.4
    y = 1.5 + (i // 2) * 2.7
    add_box(s, x, y, 6.15, 2.5, WHITE, LINE)
    add_text(s, x + 0.2, y + 0.12, 5.7, 0.35, h, 16, True, TEAL_DARK)
    add_text(s, x + 0.2, y + 0.55, 5.75, 1.8, body, 13, False, INK)

# 10 copy table
s = new_slide("바로 쓸 수 있는 화면 문구 표", "5~7쪽 캡처 옆에 이 표를 축소해 넣어도 됩니다.", "카피")
rows = [
    ("위치", "넣을 문구"),
    ("스플래시", "오늘의 마음을 천천히 정리해 보아요"),
    ("로그인", "받으신 8자리 번호를 눌러주세요"),
    ("홈 · 오전", "오전 미션을 시작해 주세요."),
    ("홈 · 오후", "오후 미션을 시작해 주세요."),
    ("홈 · 오전 놓침", "오전 미션을 놓치셨어요. 오후 미션을 시작해 주세요."),
    ("홈 · 완료", "오전·오후 미션을 모두 완료했어요!"),
    ("측정 중", "편하게 앉아 말씀하지 마시고 잠시 기다려 주세요"),
    ("측정 안내 박스", "모든 단계를 완료하면 결과를 볼 수 있어요. 심박변이도 측정 → 마음 상태 설문 → 마음일기"),
    ("측정 실패", "측정값이 정상적으로 나오지 않았습니다. 다시 측정해 주세요."),
    ("연결 실패", "기기 응답이 없습니다. 전원과 거리를 확인해 주세요."),
]
add_box(s, 0.45, 1.45, 3.3, 0.42, TEAL)
add_box(s, 3.75, 1.45, 9.1, 0.42, TEAL)
add_text(s, 0.55, 1.5, 3.1, 0.32, "위치", 13, True, WHITE)
add_text(s, 3.85, 1.5, 8.9, 0.32, "넣을 문구", 13, True, WHITE)
for i, (a, b) in enumerate(rows[1:]):
    y = 1.9 + i * 0.48
    bg = WHITE if i % 2 == 0 else TEAL_SOFT
    add_box(s, 0.45, y, 3.3, 0.46, bg, LINE)
    add_box(s, 3.75, y, 9.1, 0.46, bg, LINE)
    add_text(s, 0.55, y + 0.06, 3.1, 0.34, a, 12, True, TEAL_DARK)
    add_text(s, 3.85, y + 0.06, 8.9, 0.34, b, 12, False, INK)

# 11 screenshot list
s = new_slide("보고서에 넣으면 좋은 스크린샷 목록", "빈 페이지에 이미지만 넣지 말고, 캡처 아래 한 줄 캡션을 답니다.", "첨부")
shots = [
    ("01 스플래시", "브랜드와 한 줄 슬로건"),
    ("02 로그인 키패드", "8자리, TTS, 큰 숫자"),
    ("03 홈 · 오전 시작", "상단 문구 + 빈 별 + 3카드"),
    ("04 홈 · 오전 놓침", "두 문장 상단 안내"),
    ("05 홈 · 별 5종", "왼/오/가득/특수"),
    ("06 측정 안내", "패치·블루투스 설명"),
    ("07 측정 연결", "원 밖 상태 문구"),
    ("08 측정 5분", "파형 + 하단 고정 안내"),
    ("09 결과 유효", "확인 버튼"),
    ("10 결과 무효", "재측정만"),
    ("11 설문 안내/문항", "큰 선택지"),
    ("12 마음일기", "녹음 중 상태"),
    ("13 사분면 결과", "예측 위치"),
    ("14 피드백", "일치/다름/모름"),
    ("15 추가 측정 비활성", "왜 잠겼는지 한 줄"),
]
for i, (h, cap) in enumerate(shots):
    x = 0.45 + (i % 5) * 2.55
    y = 1.5 + (i // 5) * 1.85
    add_box(s, x, y, 2.4, 1.65, WHITE, LINE)
    add_text(s, x + 0.1, y + 0.2, 2.2, 0.55, h, 13, True, TEAL_DARK, PP_ALIGN.CENTER)
    add_text(s, x + 0.1, y + 0.8, 2.2, 0.65, cap, 12, False, MUTED, PP_ALIGN.CENTER)

# 12 checklist
s = new_slide("제출 전 체크리스트", "담당자에게 넘기기 전에 이 여덟 가지만 보면 됩니다.", "마무리")
checks = [
    "1쪽 기술/사업 목표가 서로 다른 말로 적혀 있는가",
    "2쪽 사용 목표에 ‘설명 없이’, ‘큰 글씨’, ‘실패해도 다시’가 있는가",
    "3쪽 대상(시니어·임상 태블릿)이 한 줄이라도 있는가",
    "4쪽 흐름도에 재측정·추가 측정 분기가 있는가",
    "5쪽 홈 상단 문구 4가지가 표로 있는가",
    "6쪽 유효 측정만 서버에 올린다는 문장이 있는가",
    "7쪽 피드백에 reasonCode가 없다는 점이 맞는가",
    "스크린샷마다 캡션 한 줄이 있는가",
]
for i, c in enumerate(checks):
    y = 1.5 + i * 0.65
    add_box(s, 0.45, y, 12.4, 0.55, WHITE, LINE)
    add_text(s, 0.7, y + 0.08, 12.0, 0.4, "☐   " + c, 16, False, INK)

prs.save(OUT)
print(OUT)
