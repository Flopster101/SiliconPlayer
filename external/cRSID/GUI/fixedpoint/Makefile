
all: precalc test

test: FORCE
	gcc  test.c  -o test  -lm
	./test


precalc: FORCE
	gcc  precalc.c  -o precalc  -lm
	./precalc
	-diagram tables.raw


FORCE:


