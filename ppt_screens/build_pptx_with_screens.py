# -*- coding: utf-8 -*-
"""하루마음 중간발표 PPT에 앱 화면 스크린샷을 넣는다."""
from __future__ import annotations

import copy
import os
from pptx import Presentation
from pptx.util import Emu, Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.oxml.ns import qn
from lxml import etree

ROOT = r"d:\NNNNNNNNNNNN\TMind"
SRC = os.path.join(ROOT, "하루마음_중간발표.pptx")
OUT = os.path.join(ROOT, "하루마음_중간발표.pptx")
SCR = os.path.join(ROOT, "ppt_screens")

TEAL = RGBColor(0x0F, 0x7A, 0x6B)
TEAL_DARK = RGBColor(0x0A, 0x5C, 0x50)
INK = RGBColor(0x1A, 0x1A, 0x1A)
MUTED = RGBColor(0x5A, 0x6A, 0x68)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
CARD = RGBColor(0xF4, 0xF8, 0xF7)
ACCENT_BG = RGBColor(0xE6, 0xF3, 0xF0)

# slide index (0-based) -> screenshot + caption
# 화면 설명 슬라이드에 실제/생성 스크린샷을 붙인다.
SCREEN_MAP = {
    8: ("screen_login.jpg", "로그인 화면", "8자리 참여 번호로 시작합니다"),
    9: ("screen_home.png", "홈 · 오늘의 기록", "별 7칸 + 미션 카드"),
    11: ("screen_measure_real.png", "심박변이도 측정", "5분 타이머 + 파형"),
    13: ("screen_hrv_result.jpg", "측정 결과", "심박수 · 심박변이도(ms)"),
    14: ("screen_survey.png", "마음 상태 설문", "서버 문항 · 5지 선다"),
    15: ("screen_diary.jpg", "마음일기", "음성으로 오늘을 남깁니다"),
    16: ("screen_feedback.jpg", "분석 결과 · 피드백", "사분면 + 맞아요/아니에요"),
}

GALLERY = [
    ("screen_login.jpg", "로그인"),
    ("screen_home.png", "홈"),
    ("screen_measure_real.png", "측정"),
    ("screen_hrv_result.jpg", "측정결과"),
    ("screen_survey.png", "설문"),
    ("screen_diary.jpg", "일기"),
    ("screen_feedback.jpg", "피드백"),
]


def set_run(run, text, size=14, bold=False, color=INK, font="Malgun Gothic"):
    run.text = text
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = font
    rPr = run._r.get_or_add_rPr()
    ea = rPr.find(qn("a:ea"))
    if ea is None:
        ea = etree.SubElement(rPr, qn("a:ea"))
    ea.set("typeface", font)


def add_textbox(slide, left, top, width, height, text, size=14, bold=False, color=INK, align=PP_ALIGN.LEFT):
    box = slide.shapes.add_textbox(left, top, width, height)
    tf = box.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.alignment = align
    set_run(p.add_run() if not p.runs else p.runs[0], "", size, bold, color)
    # clear default
    if p.runs:
        set_run(p.runs[0], text, size, bold, color)
    else:
        r = p.add_run()
        set_run(r, text, size, bold, color)
    return box


def clear_body_shapes(slide, keep_min_top_emu=1100000):
    """헤더/푸터만 남기고 본문 도형 제거."""
    to_del = []
    for shape in list(slide.shapes):
        # full-bleed bg rectangles and left accent bar: keep if left==0 and covers full
        if shape.left == 0 and shape.width >= 12000000:
            continue
        if shape.left == 0 and shape.width < 250000 and shape.height > 6000000:
            continue
        # header band texts near top
        if shape.top < 1100000:
            continue
        # footer
        if shape.top > 6400000:
            continue
        to_del.append(shape)
    for shape in to_del:
        sp = shape._element
        sp.getparent().remove(sp)


def rebuild_screen_slide(slide, img_name, caption_title, caption_sub, bullets):
    clear_body_shapes(slide)

    # left card
    left = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE, Emu(384048), Emu(1207008), Emu(6200000), Emu(5000000)
    )
    left.fill.solid()
    left.fill.fore_color.rgb = CARD
    left.line.fill.background()

    add_textbox(slide, Emu(548640), Emu(1350000), Emu(5800000), Emu(360000),
                caption_title, size=22, bold=True, color=TEAL_DARK)
    add_textbox(slide, Emu(548640), Emu(1750000), Emu(5800000), Emu(280000),
                caption_sub, size=13, color=MUTED)

    body = slide.shapes.add_textbox(Emu(548640), Emu(2150000), Emu(5800000), Emu(3800000)
    )
    tf = body.text_frame
    tf.word_wrap = True
    first = True
    for line in bullets:
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        p.space_after = Pt(8)
        r = p.add_run()
        set_run(r, "•  " + line, size=15, color=INK)

    # phone screenshot on right
    path = os.path.join(SCR, img_name)
    if not os.path.exists(path):
        return
    # phone frame
    frame = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE, Emu(6900000), Emu(1180000), Emu(4800000), Emu(5100000)
    )
    frame.fill.solid()
    frame.fill.fore_color.rgb = WHITE
    frame.line.color.rgb = TEAL

    pic_left = Emu(7050000)
    pic_top = Emu(1320000)
    pic_w = Emu(4500000)
    pic_h = Emu(4700000)
    slide.shapes.add_picture(path, pic_left, pic_top, width=pic_w, height=pic_h)

    badge = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE, Emu(7050000), Emu(1180000), Emu(2200000), Emu(320000)
    )
    badge.fill.solid()
    badge.fill.fore_color.rgb = TEAL
    badge.line.fill.background()
    add_textbox(slide, Emu(7050000), Emu(1200000), Emu(2200000), Emu(280000),
                "앱 실제 화면", size=12, bold=True, color=WHITE, align=PP_ALIGN.CENTER)


def add_gallery_slide(prs, after_index):
    """흐름 슬라이드 다음에 화면 모음 슬라이드 삽입."""
    blank = prs.slide_layouts[6] if len(prs.slide_layouts) > 6 else prs.slide_layouts[0]
    # duplicate by adding blank then move - python-pptx insert is awkward; append then reorder XML
    slide = prs.slides.add_slide(blank)

    # bg
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Emu(0), Emu(0), prs.slide_width, prs.slide_height)
    bg.fill.solid()
    bg.fill.fore_color.rgb = WHITE
    bg.line.fill.background()
    bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Emu(0), Emu(0), Emu(128016), prs.slide_height)
    bar.fill.solid()
    bar.fill.fore_color.rgb = TEAL
    bar.line.fill.background()

    add_textbox(slide, Emu(384048), Emu(146304), Emu(9875520), Emu(256032),
                "NEST  ·  하루마음(TMind)  ·  중간발표", size=11, color=MUTED)
    add_textbox(slide, Emu(384048), Emu(384048), Emu(10607040), Emu(384048),
                "앱 화면으로 한눈에 보기", size=28, bold=True, color=INK)
    add_textbox(slide, Emu(384048), Emu(786384), Emu(11247120), Emu(292608),
                "앞에서 말한 각 단계가 실제로 이런 화면입니다. 오른쪽 설명과 짝을 맞춰 보세요.", size=14, color=MUTED)

    n = len(GALLERY)
    gap = 120000
    total_w = 11300000
    cell_w = (total_w - gap * (n - 1)) // n
    top = 1250000
    for i, (fname, label) in enumerate(GALLERY):
        left = 400000 + i * (cell_w + gap)
        path = os.path.join(SCR, fname)
        if not os.path.exists(path):
            continue
        # label
        add_textbox(slide, Emu(left), Emu(top), Emu(cell_w), Emu(280000),
                    f"{i+1}. {label}", size=12, bold=True, color=TEAL_DARK, align=PP_ALIGN.CENTER)
        slide.shapes.add_picture(path, Emu(left + 40000), Emu(top + 320000),
                                 width=Emu(cell_w - 80000), height=Emu(4600000))

    add_textbox(slide, Emu(411480), Emu(6565392), Emu(7772400), Emu(201168),
                "하루마음(TMind) 중간발표  ·  현재 앱 소스 기준", size=10, color=MUTED)
    add_textbox(slide, Emu(10607040), Emu(6565392), Emu(1188720), Emu(201168),
                "화면", size=10, color=MUTED, align=PP_ALIGN.RIGHT)

    # move this new slide to after_index+1
    sldIdLst = prs.slides._sldIdLst
    children = list(sldIdLst)
    new_el = children[-1]
    sldIdLst.remove(new_el)
    # insert after after_index
    ref = list(sldIdLst)[after_index]
    ref.addnext(new_el)


BULLETS = {
    8: [
        "키패드에 8자리 참여 번호를 입력합니다.",
        "처음이면 이름·성별·나이를 한 번만 묻습니다.",
        "스피커 버튼으로 안내를 읽어 줍니다.",
        "서버 로그인 후 홈(오늘의 기록)으로 이동합니다.",
    ],
    9: [
        "인사말·별 7칸·미션 카드가 한 화면에 있습니다.",
        "심박 → 설문 → 일기 순서로만 진행됩니다.",
        "끝난 카드는 초록 체크, 남은 일은 강조색입니다.",
        "별 칸은 서버 /today stars 값을 그대로 그립니다.",
    ],
    11: [
        "패치(RENST) 연결 후 5분 동안 측정합니다.",
        "원 타이머와 함께 심전도 파형이 지나갑니다.",
        "가로 회전해도 세로로 고정되어 측정이 끊기지 않습니다.",
        "끝나면 심박수·심박변이도(ms) 결과 화면으로 갑니다.",
    ],
    13: [
        "심박수(회/분)와 심박변이도(SDNN, ms)를 크게 보여 줍니다.",
        "위쪽에는 Raw ECG 구간이 표시됩니다.",
        "유효하면 「확인」 후 설문으로, 아니면 재측정입니다.",
        "결과 저장 뒤에는 같은 세션을 덮어쓰지 않습니다.",
    ],
    14: [
        "문항·보기·polarity는 서버에서 내려줍니다.",
        "한 문항씩 5개 보기 중 하나를 고릅니다.",
        "답은 1~5 숫자로 서버에 저장됩니다.",
        "글씨를 키워도 선택지가 잘 보이도록 안내 박스는 제거했습니다.",
    ],
    15: [
        "글로 쓰지 않고 말로 오늘을 남깁니다.",
        "녹음 버튼·파형·제한 시간(최대 약 3분)이 있습니다.",
        "파일이 서버로 올라간 뒤 세 미션 완료를 확인합니다.",
        "심박·설문·일기가 모두 끝나야 결과 화면으로 갑니다.",
    ],
    16: [
        "심박으로 예측한 마음 위치를 사분면에 표시합니다.",
        "「맞아요 / 아니에요 / 잘 모름」으로 피드백합니다.",
        "이 응답이 연구의 정답 라벨이 됩니다.",
        "끝나면 홈으로 돌아와 별·추가 측정을 이어갑니다.",
    ],
}


def main():
    prs = Presentation(SRC)
    print("loaded", len(prs.slides), "slides")

    for idx, (img, title, sub) in SCREEN_MAP.items():
        if idx >= len(prs.slides):
            continue
        slide = prs.slides[idx]
        rebuild_screen_slide(slide, img, title, sub, BULLETS.get(idx, [sub]))
        print("updated slide", idx + 1, title)

    # insert gallery after flow slide (index 7 = slide 8)
    add_gallery_slide(prs, after_index=7)
    print("inserted gallery after slide 8")

    prs.save(OUT)
    print("saved", OUT)


if __name__ == "__main__":
    main()
